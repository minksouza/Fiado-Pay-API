package edu.ucsal.fiadopay.antifraud;


import edu.ucsal.fiadopay.domain.Payment;


public interface AntiFraudRule {
/** returns a non-empty reason when suspicious, otherwise null/empty */
String inspect(Payment p);
}