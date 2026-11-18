package com.expiryguard.server.bdd;

import com.expiryguard.server.entity.Batch;
import com.expiryguard.server.entity.MovementType;
import com.expiryguard.server.entity.StockMovement;
import com.expiryguard.server.repository.BatchRepository;
import com.expiryguard.server.repository.StockMovementRepository;
import com.expiryguard.server.service.StockMovementService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Step definitions: service-level BDD using Mockito (no database needed). */
public class StockMovementSteps {

    private BatchRepository batches;
    private StockMovementRepository movements;
    private StockMovementService service;
    private Batch batch;
    private ResponseStatusException error;
    private StockMovement result;

    @Before
    public void init() {
        batches = mock(BatchRepository.class);
        movements = mock(StockMovementRepository.class);
        service = new StockMovementService(batches, movements);
        when(movements.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
        error = null; result = null;
    }

    private void givenBatch(int qty, LocalDate expiry) {
        batch = new Batch();
        batch.setQuantity(qty);
        batch.setExpiryDate(expiry);
        when(batches.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
    }

    @Given("a batch with {int} units that expired {int} day(s) ago")
    public void expiredBatch(int qty, int days) { givenBatch(qty, LocalDate.now().minusDays(days)); }

    @Given("a batch with {int} units that expires in {int} days")
    public void validBatch(int qty, int days) { givenBatch(qty, LocalDate.now().plusDays(days)); }

    @When("the pharmacist records a {word} movement of {int} units")
    public void record(String type, int qty) {
        try { result = service.record(1L, MovementType.valueOf(type), qty, "bdd"); }
        catch (ResponseStatusException e) { error = e; }
    }

    @Then("the movement is rejected with status {int}")
    public void rejected(int code) {
        assertNotNull(error, "Expected rejection");
        assertEquals(code, error.getStatusCode().value());
    }

    @Then("the movement is accepted")
    public void accepted() { assertNull(error); assertNotNull(result); }

    @Then("the batch quantity is {int}")
    public void quantity(int q) { assertEquals(q, batch.getQuantity()); }

    @Then("the outcome is {string}")
    public void outcome(String o) {
        if (o.equals("accepted")) accepted(); else assertNotNull(error);
    }
}
