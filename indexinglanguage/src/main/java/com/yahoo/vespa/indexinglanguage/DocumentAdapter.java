// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.Document;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;

/**
 * @author Simon Thoresen Hult
 */
public interface DocumentAdapter extends FieldValueAdapter {

    public Document getFullOutput();

    public Document getUpdatableOutput();
}
