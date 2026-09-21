package com.spendlocker.dao;

import com.spendlocker.TestSupport;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;
import com.spendlocker.model.PaymentMethod;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * All DAO CRUD tests share a single vault-open lifecycle in this one class, since
 * {@link com.spendlocker.db.DatabaseManager} is a process-wide singleton and Surefire's
 * fork/JVM reuse behavior isn't something to rely on for coordinating state across classes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DaoIntegrationTest {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final BudgetDao budgetDao = new BudgetDao();

    @BeforeAll
    static void openVault() {
        TestSupport.ensureVaultOpen();
    }

    @Test
    @Order(1)
    void expenseInsertUpdateDeleteRoundTrips() {
        Expense expense = new Expense();
        expense.setTransactionDate(LocalDate.now().toString());
        expense.setAmount(42.50);
        expense.setCategory("__TestCategory__");
        expense.setMerchantOrVendor("__TestMerchant__");
        expense.setPaymentMethod(PaymentMethod.CASH);
        expense.setNotes("unit test row");

        expenseDao.insert(expense);
        assertTrue(expense.getId() > 0);

        List<Expense> all = expenseDao.findAll();
        assertTrue(all.stream().anyMatch(e -> e.getId() == expense.getId()));

        expense.setAmount(999.0);
        expense.setNotes("updated");
        expenseDao.update(expense);
        Expense reloaded = expenseDao.findAll().stream()
                .filter(e -> e.getId() == expense.getId()).findFirst().orElseThrow();
        assertEquals(999.0, reloaded.getAmount(), 0.001);
        assertEquals("updated", reloaded.getNotes());

        expenseDao.softDelete(expense.getId());
        assertTrue(expenseDao.findAll().stream().noneMatch(e -> e.getId() == expense.getId()));
        assertTrue(expenseDao.findDeleted().stream().anyMatch(e -> e.getId() == expense.getId()));

        expenseDao.restore(expense.getId());
        assertTrue(expenseDao.findAll().stream().anyMatch(e -> e.getId() == expense.getId()));

        expenseDao.hardDelete(expense.getId());
        assertTrue(expenseDao.findAll().stream().noneMatch(e -> e.getId() == expense.getId()));
        assertTrue(expenseDao.findDeleted().stream().noneMatch(e -> e.getId() == expense.getId()));
    }

    @Test
    @Order(2)
    void investmentInsertRoundTripsWithCustomType() {
        Investment inv = new Investment();
        inv.setAssetName("__TestAsset__");
        inv.setAssetTicker("TST");
        inv.setInvestmentType("__CustomTestType__");
        inv.setPurchaseDate(LocalDate.now().toString());
        inv.setPrincipalAmount(100.0);
        inv.setCurrentUnitPrice(10.0);
        inv.setTotalUnits(10.0);

        investmentDao.insert(inv);
        assertTrue(inv.getId() > 0);

        Investment reloaded = investmentDao.findAll().stream()
                .filter(i -> i.getId() == inv.getId()).findFirst().orElseThrow();
        assertEquals("__CustomTestType__", reloaded.getInvestmentType());
        assertEquals(100.0, reloaded.getCurrentTotalValue(), 0.001);

        investmentDao.softDelete(inv.getId());
        assertTrue(investmentDao.findAll().stream().noneMatch(i -> i.getId() == inv.getId()));
        assertTrue(investmentDao.findDeleted().stream().anyMatch(i -> i.getId() == inv.getId()));

        investmentDao.hardDelete(inv.getId());
        assertTrue(investmentDao.findDeleted().stream().noneMatch(i -> i.getId() == inv.getId()));
    }

    @Test
    @Order(3)
    void categoryAddIfAbsentIsIdempotent() {
        categoryDao.addIfAbsent("__UnitTestCategory__");
        categoryDao.addIfAbsent("__UnitTestCategory__");
        long occurrences = categoryDao.findAll().stream()
                .filter(c -> c.equals("__UnitTestCategory__")).count();
        assertEquals(1, occurrences);
    }

    @Test
    @Order(4)
    void budgetUpsertInsertsThenUpdates() {
        String category = "__UnitTestBudgetCategory__";
        budgetDao.upsert(category, 500.0);
        assertEquals(500.0, budgetDao.findAll().get(category), 0.001);

        budgetDao.upsert(category, 750.0);
        assertEquals(750.0, budgetDao.findAll().get(category), 0.001);

        budgetDao.delete(category);
        assertFalse(budgetDao.findAll().containsKey(category));
    }
}
