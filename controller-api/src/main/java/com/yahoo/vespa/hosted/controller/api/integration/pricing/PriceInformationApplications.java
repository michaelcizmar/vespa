// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.math.BigDecimal;

public record PriceInformationApplications(BigDecimal listPriceWithSupport, BigDecimal volumeDiscount, BigDecimal committedAmountDiscount,
                                           BigDecimal enclaveDiscount, BigDecimal totalAmount) {

}