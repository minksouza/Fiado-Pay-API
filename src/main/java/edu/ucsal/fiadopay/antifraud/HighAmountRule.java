package edu.ucsal.fiadopay.antifraud;


import org.springframework.stereotype.Component;

import edu.ucsal.fiadopay.annotations.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;


@AntiFraud(name = "HighAmount", threshold = 5000.0)
@Component
public class HighAmountRule implements AntiFraudRule {
@Override
public String inspect(Payment p) {
if (p.getAmount().doubleValue() > 5000.0) {
return "AMOUNT_TOO_HIGH";
}
return null;
}
}