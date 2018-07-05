// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
abstract public class PredicateOperator extends Predicate {

    public abstract List<Predicate> getOperands();
}
