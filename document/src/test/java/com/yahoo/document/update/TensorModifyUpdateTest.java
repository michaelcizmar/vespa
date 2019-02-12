// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.update.TensorModifyUpdate.Operation;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class TensorModifyUpdateTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void convert_to_compatible_type_with_only_mapped_dimensions() {
        assertConvertToCompatible("tensor(x{})", "tensor(x[])");
        assertConvertToCompatible("tensor(x{})", "tensor(x[10])");
        assertConvertToCompatible("tensor(x{})", "tensor(x{})");
        assertConvertToCompatible("tensor(x{},y{},z{})", "tensor(x[],y[10],z{})");
    }

    private static void assertConvertToCompatible(String expectedType, String inputType) {
        assertEquals(expectedType, TensorModifyUpdate.convertToCompatibleType(TensorType.fromSpec(inputType)).toString());
    }

    @Test
    public void use_of_incompatible_tensor_type_throws() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tensor type 'tensor(x[3])' is not compatible as it contains some indexed dimensions");
        new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE,
                new TensorFieldValue(Tensor.from("tensor(x[3])", "{{x:1}:3}")));
    }

    @Test
    public void apply_modify_update_operations() {
        assertApplyTo("tensor(x{},y{})", Operation.REPLACE,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:0}", "{{x:0,y:0}:1,{x:0,y:1}:0}");
        assertApplyTo("tensor(x{},y{})", Operation.ADD,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:5}");
        assertApplyTo("tensor(x{},y{})", Operation.MULTIPLY,
                "{{x:0,y:0}:3, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:3,{x:0,y:1}:6}");
        assertApplyTo("tensor(x[1],y[2])", Operation.REPLACE,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:0}", "{{x:0,y:0}:1,{x:0,y:1}:0}");
        assertApplyTo("tensor(x[1],y[2])", Operation.ADD,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:5}");
        assertApplyTo("tensor(x[1],y[2])", Operation.MULTIPLY,
                "{{x:0,y:0}:3, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:3,{x:0,y:1}:6}");
    }

    private void assertApplyTo(String spec, Operation op, String init, String update, String expected) {
        DocumentTypeManager types = new DocumentTypeManager();
        DocumentType x = new DocumentType("x");
        x.addField(new Field("f", new TensorDataType(TensorType.fromSpec(spec))));
        types.registerDocumentType(x);

        Document document = new Document(types.getDocumentType("x"), new DocumentId("doc:test:x"));
        document.setFieldValue("f", new TensorFieldValue(Tensor.from(spec, init)));

        FieldUpdate.create(document.getField("f"))
            .addValueUpdate(new TensorModifyUpdate(op, new TensorFieldValue(Tensor.from("tensor(x{},y{})", update))))
            .applyTo(document);
        Tensor result = ((TensorFieldValue) document.getFieldValue("f")).getTensor().get();
        assertEquals(Tensor.from(spec, expected), result);
    }

}
