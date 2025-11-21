package edu.ucsal.fiadopay.payment;

import edu.ucsal.fiadopay.domain.Payment;

public interface PaymentProcessor {
    boolean process(Payment payment);
}
