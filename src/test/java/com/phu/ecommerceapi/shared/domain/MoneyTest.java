package com.phu.ecommerceapi.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesAmountToCurrencyScale() {
        Money money = Money.of("10", "USD");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsAmountWithTooManyFractionDigits() {
        assertThatThrownBy(() -> Money.of("10.001", "USD"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void addsMoneyWithSameCurrency() {
        Money result = Money.of("10.00", "USD").add(Money.of("2.50", "USD"));

        assertThat(result).isEqualTo(Money.of("12.50", "USD"));
    }

    @Test
    void rejectsOperationsAcrossCurrencies() {
        Money usd = Money.of("10.00", "USD");
        Money thb = new Money(BigDecimal.TEN, Currency.getInstance("THB"));

        assertThatThrownBy(() -> usd.add(thb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Money currency mismatch");
    }
}
