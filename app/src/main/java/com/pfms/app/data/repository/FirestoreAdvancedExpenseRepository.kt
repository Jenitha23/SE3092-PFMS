package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.firebase.userIds
import com.pfms.app.data.local.dao.ExpenseCategoryDao
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.data.model.DefaultFinancialData
import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.Recurrence
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.model.SubscriptionFlag
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreAdvancedExpenseRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val categoryDao: ExpenseCategoryDao,
    private val recurringDao: RecurringExpenseDao
) : AdvancedExpenseRepository {

    private fun uid(): String = auth.currentUser?.uid ?: error("User must be authenticated")

    private fun categoriesRef(userId: String = uid()) = firestore
        .collection(FirestoreCollections.USERS)
        .document(userId)
        .collection(FirestoreCollections.CATEGORIES)

    private fun templatesRef(userId: String = uid()) = firestore
        .collection(FirestoreCollections.USERS)
        .document(userId)
        .collection(FirestoreCollections.RECURRING_TEMPLATES)

    private fun expensesRef(userId: String = uid()) = firestore
        .collection(FirestoreCollections.USERS)
        .document(userId)
        .collection(FirestoreCollections.EXPENSES)

    private fun DocumentSnapshot.asCategory(): ExpenseCategory {
        val classificationStr = getString("classification") ?: getString("defaultClassification")
        val isCommitted = classificationStr?.equals("COMMITTED", ignoreCase = true) == true ||
                classificationStr?.equals("Committed", ignoreCase = true) == true

        val isDefault = getBoolean("isDefault") ?: !(getBoolean("custom") ?: false)
        val isCustom = getBoolean("custom") ?: (!isDefault)
        val isArchived = getBoolean("archived") ?: (getBoolean("isArchived") ?: false)

        return ExpenseCategory(
            id = id,
            name = getString("name").orEmpty(),
            classification = if (isCommitted) ExpenseClassification.COMMITTED else ExpenseClassification.DISCRETIONARY,
            custom = isCustom,
            archived = isArchived
        )
    }

    private fun DocumentSnapshot.asTemplate(): RecurringExpense = RecurringExpense(
        id = id,
        title = getString("title").orEmpty(),
        categoryId = getString("categoryId").orEmpty(),
        categoryName = getString("categoryName").orEmpty(),
        classification = enumOr(getString("classification"), ExpenseClassification.COMMITTED),
        amountMinor = getLong("amountMinor") ?: 0L,
        paymentMethod = enumOr(getString("paymentMethod"), ExpensePaymentMethod.CASH),
        recurrence = enumOr(getString("recurrence"), Recurrence.MONTHLY),
        nextDueDate = getString("nextDueDate").orEmpty(),
        active = getBoolean("active") ?: (getBoolean("isActive") ?: true),
        subscription = getBoolean("subscription") ?: (getBoolean("isSubscription") ?: false),
        flag = enumOr(getString("flag"), SubscriptionFlag.NONE),
        reminderDate = getString("reminderDate").orEmpty()
    )

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

    private fun CollectionReference.snapshots(): Flow<QuerySnapshot> = callbackFlow {
        val listener = addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { listener.remove() }
    }

    override fun categories(): Flow<List<ExpenseCategory>> = auth.userIds().flatMapLatest { userId ->
        if (userId == null) flowOf(emptyList()) else channelFlow {
            launch {
                categoriesRef(userId).snapshots().collect { snapshot ->
                    val entities = snapshot.documents.map { it.asCategory().toEntity(userId) }
                    // A cached snapshot may be incomplete after clearing Firestore persistence.
                    if (snapshot.metadata.isFromCache) categoryDao.upsertCategories(entities)
                    else categoryDao.replaceForUser(userId, entities)
                }
            }
            categoryDao.observeCategories(userId).collect { rows -> send(rows.map { it.toDomain() }) }
        }.onStart { emit(emptyList()) }
    }.flowOn(Dispatchers.IO)

    override fun templates(): Flow<List<RecurringExpense>> = auth.userIds().flatMapLatest { userId ->
        if (userId == null) flowOf(emptyList()) else channelFlow {
            launch {
                templatesRef(userId).snapshots().collect { snapshot ->
                    val entities = snapshot.documents.map { it.asTemplate().toEntity(userId) }
                    if (snapshot.metadata.isFromCache) recurringDao.upsertTemplates(entities)
                    else recurringDao.replaceForUser(userId, entities)
                }
            }
            recurringDao.observeTemplates(userId).collect { rows -> send(rows.map { it.toDomain() }) }
        }.onStart { emit(emptyList()) }
    }.flowOn(Dispatchers.IO)

    override suspend fun refreshTemplates() {
        val userId = uid()
        val snapshot = templatesRef(userId).get(Source.SERVER).await()
        recurringDao.replaceForUser(userId, snapshot.documents.map { it.asTemplate().toEntity(userId) })
    }

    override suspend fun initializeDefaults() {
        val currentUid = uid()
        val existing = categoriesRef(currentUid).get().await().documents.map { it.id }.toSet()
        val missingDefaults = DefaultFinancialData.expenseCategories.filter { it.id !in existing }
        if (missingDefaults.isEmpty()) return

        val batch = firestore.batch()
        val now = System.currentTimeMillis()

        missingDefaults.forEach { category ->
            val classificationEnum = if (category.classification.equals("Committed", true)) {
                ExpenseClassification.COMMITTED
            } else {
                ExpenseClassification.DISCRETIONARY
            }

            val docRef = categoriesRef(currentUid).document(category.id)
            val data = mapOf(
                "id" to category.id,
                "name" to category.name,
                "classification" to classificationEnum.name,
                "defaultClassification" to category.classification,
                "custom" to false,
                "isDefault" to true,
                "archived" to false,
                "isArchived" to false,
                "createdAt" to now,
                "updatedAt" to now
            )
            batch.set(docRef, data)
        }

        batch.commit().await()
    }

    override suspend fun createCategory(name: String, classification: ExpenseClassification) {
        val currentUid = uid()
        val cleaned = name.trim()
        require(cleaned.isNotEmpty()) { "Category name cannot be empty." }
        require(cleaned.length <= 50) { "Category name must be 1 to 50 characters." }

        val existing = categoriesRef(currentUid).get().await().documents
        require(existing.none { it.getString("name")?.trim().equals(cleaned, ignoreCase = true) }) {
            "A category with name '$cleaned' already exists."
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val data = mapOf(
            "id" to id,
            "name" to cleaned,
            "classification" to classification.name,
            "defaultClassification" to if (classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary",
            "custom" to true,
            "isDefault" to false,
            "archived" to false,
            "isArchived" to false,
            "createdAt" to now,
            "updatedAt" to now
        )

        categoriesRef(currentUid).document(id).set(data).await()

        categoryDao.upsertCategory(ExpenseCategory(id, cleaned, classification, custom = true).toEntity(currentUid))
    }

    override suspend fun updateCategory(category: ExpenseCategory) {
        val currentUid = uid()
        require(category.custom) { "Default categories cannot be renamed or archived." }
        val cleaned = category.name.trim()
        require(cleaned.isNotEmpty()) { "Category name cannot be empty." }
        require(cleaned.length <= 50) { "Category name must be 1 to 50 characters." }

        val ref = categoriesRef(currentUid).document(category.id)
        val current = ref.get().await()
        require(current.exists()) { "Category not found." }

        val isCustom = current.getBoolean("custom") ?: !(current.getBoolean("isDefault") ?: false)
        require(isCustom) { "Default categories cannot be edited." }

        val allDocs = categoriesRef(currentUid).get().await().documents
        val duplicate = allDocs.any {
            it.id != category.id && it.getString("name")?.trim().equals(cleaned, ignoreCase = true)
        }
        require(!duplicate) { "Another category named '$cleaned' already exists." }

        val now = System.currentTimeMillis()

        val updateData = mapOf(
            "name" to cleaned,
            "classification" to category.classification.name,
            "defaultClassification" to if (category.classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary",
            "archived" to category.archived,
            "isArchived" to category.archived,
            "updatedAt" to now
        )

        ref.update(updateData).await()

        categoryDao.upsertCategory(category.copy(name = cleaned).toEntity(currentUid))
    }

    override suspend fun saveTemplate(template: RecurringExpense) {
        val normalized = if (template.subscription && template.flag == SubscriptionFlag.CANCEL) {
            template.copy(active = false, reminderDate = "")
        } else template
        saveNormalizedTemplate(normalized)
    }

    private suspend fun saveNormalizedTemplate(template: RecurringExpense) {
        val currentUid = uid()
        val cleanedTitle = template.title.trim()
        require(cleanedTitle.isNotEmpty()) { "Title cannot be empty." }
        require(template.amountMinor > 0L) { "Amount must be greater than zero." }
        require(template.categoryId.isNotBlank()) { "Category must be selected." }

        // Validate date formats
        require(runCatching { LocalDate.parse(template.nextDueDate) }.isSuccess) { "Enter a valid due date (YYYY-MM-DD)." }
        if (template.reminderDate.isNotBlank()) {
            require(runCatching { LocalDate.parse(template.reminderDate) }.isSuccess) { "Enter a valid reminder date (YYYY-MM-DD)." }
        }

        val id = template.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()

        val data = mapOf(
            "id" to id,
            "title" to cleanedTitle,
            "categoryId" to template.categoryId,
            "categoryName" to template.categoryName,
            "classification" to template.classification.name,
            "amountMinor" to template.amountMinor,
            "paymentMethod" to template.paymentMethod.name,
            "recurrence" to template.recurrence.name,
            "nextDueDate" to template.nextDueDate,
            "active" to template.active,
            "isActive" to template.active,
            "subscription" to template.subscription,
            "isSubscription" to template.subscription,
            "flag" to template.flag.name,
            "reminderDate" to template.reminderDate,
            "updatedAt" to now
        )

        templatesRef(currentUid).document(id).set(data).await()

        recurringDao.upsertTemplate(template.copy(id = id, title = cleanedTitle).toEntity(currentUid))
    }

    override suspend fun deleteTemplate(templateId: String) {
        require(templateId.isNotBlank()) { "Template ID cannot be blank." }
        val currentUid = uid()
        templatesRef(currentUid).document(templateId).delete().await()
        recurringDao.deleteTemplate(currentUid, templateId)
    }

    override suspend fun confirmDue(template: RecurringExpense) {
        require(template.amountMinor > 0L) { "Amount must be greater than zero." }
        val currentUid = uid()
        val today = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE)
        val due = LocalDate.parse(template.nextDueDate)
        require(template.active && !due.isAfter(today)) { "This item is not yet due." }

        // Idempotent confirmation: deterministic expense document key prevents double confirmations
        val expenseId = "recurring_${template.id}_${template.nextDueDate}"
        val templateRef = templatesRef(currentUid).document(template.id)
        val expenseRef = firestore.collection(FirestoreCollections.USERS).document(currentUid)
            .collection(FirestoreCollections.EXPENSES).document(expenseId)

        val advancedTemplate = firestore.runTransaction { transaction ->
            val latest = transaction.get(templateRef)
            val present = transaction.get(expenseRef)

            check(latest.exists() && latest.getString("nextDueDate") == template.nextDueDate) {
                "Template due date was already updated."
            }
            check(!present.exists()) { "This occurrence was already confirmed." }
            val current = latest.asTemplate()
            check(current.active && (!current.subscription || current.flag != SubscriptionFlag.CANCEL)) {
                "This template has been paused or cancelled. Refresh and try again."
            }
            check(current.copy(amountMinor = template.amountMinor) == template) {
                "This template has changed. Refresh it before confirming."
            }

            val nextDueDate = RecurringExpenseCalculator.calculateNextDueDate(due, template.recurrence)
            val now = System.currentTimeMillis()

            transaction.set(
                expenseRef,
                mapOf(
                    "id" to expenseId,
                    "userId" to currentUid,
                    "amountMinor" to template.amountMinor,
                    "currency" to "LKR",
                    "categoryId" to template.categoryId,
                    "categoryName" to template.categoryName,
                    "classification" to template.classification.name,
                    "paymentMethod" to template.paymentMethod.name,
                    "date" to today.toString(),
                    "merchant" to template.title,
                    "note" to "Recurring expense (${template.recurrence.name.lowercase()})",
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            transaction.update(
                templateRef,
                mapOf(
                    "nextDueDate" to nextDueDate.toString(),
                    "updatedAt" to now
                )
            )
            current.copy(nextDueDate = nextDueDate.toString())
        }.await()
        recurringDao.upsertTemplate(advancedTemplate.toEntity(currentUid))
    }

    override suspend fun repeatExpense(expenseId: String) {
        val currentUid = uid()
        val original = expensesRef(currentUid).document(expenseId).get().await()
        require(original.exists()) { "Expense not found." }

        val owner = original.getString("userId")
        require(owner == null || owner == currentUid) { "Access denied: expense belongs to another user." }

        val copy = original.data.orEmpty().toMutableMap()
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE).toString()

        copy["id"] = newId
        copy["userId"] = currentUid
        copy["date"] = today
        copy["createdAt"] = now
        copy["updatedAt"] = now

        expensesRef(currentUid).document(newId).set(copy).await()
    }
}
