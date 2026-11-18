Feature: Medicine stock movement rules
  As a pharmacist
  I want the system to protect stock integrity
  So that expired medicine is never sold and stock counts stay correct

  Scenario: Prevent selling expired medicine
    Given a batch with 10 units that expired 1 day ago
    When the pharmacist records a SOLD movement of 1 units
    Then the movement is rejected with status 409
    And the batch quantity is 10

  Scenario: Receive new stock
    Given a batch with 10 units that expires in 60 days
    When the pharmacist records a RECEIVED movement of 5 units
    Then the movement is accepted
    And the batch quantity is 15

  Scenario Outline: Sale quantity boundaries
    Given a batch with 20 units that expires in 60 days
    When the pharmacist records a SOLD movement of <qty> units
    Then the outcome is "<outcome>"

    Examples:
      | qty | outcome  |
      | 0   | rejected |
      | 1   | accepted |
      | 20  | accepted |
      | 21  | rejected |
