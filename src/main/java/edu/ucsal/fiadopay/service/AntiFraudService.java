package edu.ucsal.fiadopay.service;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import edu.ucsal.fiadopay.annotations.AntiFraud;
import edu.ucsal.fiadopay.antifraud.AntiFraudRule;
import edu.ucsal.fiadopay.domain.Payment;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AntiFraudService {
private final ApplicationContext ctx;


public Optional<String> inspect(Payment p) {
Map<String, AntiFraudRule> beans = ctx.getBeansOfType(AntiFraudRule.class);
for (AntiFraudRule r : beans.values()) {
AntiFraud a = r.getClass().getAnnotation(AntiFraud.class);
if (a != null) {
String reason = r.inspect(p);
if (reason != null && !reason.isEmpty()) {
return Optional.of(a.name() + ":" + reason);
}
}
}
return Optional.empty();
}
}