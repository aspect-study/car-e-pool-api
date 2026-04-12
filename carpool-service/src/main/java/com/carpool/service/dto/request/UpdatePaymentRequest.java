package com.carpool.service.dto.request;

import com.carpool.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdatePaymentRequest(
        @NotNull(message = "amountPaid is required")
        @DecimalMin(value = "0.01", message = "amountPaid must be greater than 0")
        BigDecimal amountPaid,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod
) {}
