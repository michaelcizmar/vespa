// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

TEST(DimSpec, indexed_dimension) {
    ValueType::Dimension ref("foo", 10);
    DimSpec idx("foo", 10);
    EXPECT_EQ(idx.type(), ref);
    EXPECT_TRUE(ref.is_indexed());
    EXPECT_EQ(idx.name(), "foo");
    EXPECT_EQ(idx.size(), 10);
    EXPECT_EQ(idx.label(3), TensorSpec::Label(size_t(3)));
}

TEST(DimSpec, mapped_dimension) {
    ValueType::Dimension ref("foo");
    DimSpec map("foo", {"a", "b", "c", "d"});
    EXPECT_EQ(map.type(), ref);
    EXPECT_TRUE(ref.is_mapped());
    EXPECT_EQ(map.name(), "foo");
    EXPECT_EQ(map.size(), 4);
    EXPECT_EQ(map.label(2), TensorSpec::Label("c"));
}

TEST(DimSpec, simple_dictionary_creation) {
    auto dict = DimSpec::make_dict(5, 1, "");
    std::vector<vespalib::string> expect = {"0", "1", "2", "3", "4"};
}

TEST(DimSpec, advanced_dictionary_creation) {
    auto dict = DimSpec::make_dict(5, 3, "str_");
    std::vector<vespalib::string> expect = {"str_0", "str_3", "str_6", "str_9", "str_12"};
}

//-----------------------------------------------------------------------------

TEST(GenSpec, default_spec) {
    GenSpec spec;
    EXPECT_TRUE(spec.dims().empty());
    EXPECT_EQ(spec.cells(), CellType::DOUBLE);
    auto seq = spec.seq();
    for (size_t i = 0; i < 4096; ++i) {
        EXPECT_EQ(seq(i), (i + 1.0));        
    }
}

//-----------------------------------------------------------------------------

TensorSpec scalar_1 = TensorSpec("double").add({}, 1.0);
TensorSpec scalar_5 = TensorSpec("double").add({}, 5.0);

TEST(GenSpec, scalar_double) {
    EXPECT_EQ(GenSpec().gen(), scalar_1);
    EXPECT_EQ(GenSpec().seq_bias(5.0).gen(), scalar_5);
}

TEST(GenSpec, not_scalar_float_just_yet) {
    EXPECT_EQ(GenSpec().cells_float().gen(), scalar_1);
    EXPECT_EQ(GenSpec().cells_float().seq_bias(5.0).gen(), scalar_5);
}

//-----------------------------------------------------------------------------

TEST(Seq, seq_n) {
    GenSpec::seq_t seq = GenSpec().seq_n().seq();
    for (size_t i = 0; i < 4096; ++i) {
        EXPECT_EQ(seq(i), (i + 1.0));        
    }
}

TEST(Seq, seq_bias) {
    GenSpec::seq_t seq = GenSpec().seq_bias(13.0).seq();
    for (size_t i = 0; i < 4096; ++i) {
        EXPECT_EQ(seq(i), (i + 13.0));
    }
}

//-----------------------------------------------------------------------------

GenSpec flt() { return GenSpec().cells_float(); }
GenSpec dbl() { return GenSpec().cells_double(); }

TEST(GenSpec, value_type) {
    EXPECT_EQ(dbl().type().to_spec(), "double");
    EXPECT_EQ(flt().type().to_spec(), "double"); // NB
    EXPECT_EQ(dbl().idx("x", 10).type().to_spec(), "tensor(x[10])");
    EXPECT_EQ(flt().idx("x", 10).type().to_spec(), "tensor<float>(x[10])");
    EXPECT_EQ(dbl().map("y", {}).type().to_spec(), "tensor(y{})");
    EXPECT_EQ(flt().map("y", {}).type().to_spec(), "tensor<float>(y{})");
    EXPECT_EQ(dbl().idx("x", 10).map("y", {}).type().to_spec(), "tensor(x[10],y{})");
    EXPECT_EQ(flt().idx("x", 10).map("y", {}).type().to_spec(), "tensor<float>(x[10],y{})");
    EXPECT_EQ(dbl().map("y", 3, 1).idx("x", 10).type().to_spec(), "tensor(x[10],y{})");
    EXPECT_EQ(flt().map("y", 3, 1, "str").idx("x", 10).type().to_spec(), "tensor<float>(x[10],y{})");
}

//-----------------------------------------------------------------------------

TensorSpec basic_vector = TensorSpec("tensor(a[5])")
    .add({{"a", 0}}, 1.0)
    .add({{"a", 1}}, 2.0)
    .add({{"a", 2}}, 3.0)
    .add({{"a", 3}}, 4.0)
    .add({{"a", 4}}, 5.0);

TensorSpec float_vector = TensorSpec("tensor<float>(a[5])")
    .add({{"a", 0}}, 1.0)
    .add({{"a", 1}}, 2.0)
    .add({{"a", 2}}, 3.0)
    .add({{"a", 3}}, 4.0)
    .add({{"a", 4}}, 5.0);

TensorSpec custom_vector = TensorSpec("tensor(a[5])")
    .add({{"a", 0}}, 5.0)
    .add({{"a", 1}}, 4.0)
    .add({{"a", 2}}, 3.0)
    .add({{"a", 3}}, 2.0)
    .add({{"a", 4}}, 1.0);

TEST(GenSpec, generating_basic_vector) {
    EXPECT_EQ(GenSpec().idx("a", 5).gen(), basic_vector);
}

TEST(GenSpec, generating_float_vector) {
    EXPECT_EQ(GenSpec().idx("a", 5).cells_float().gen(), float_vector);
}

TEST(GenSpec, generating_custom_vector) {
    GenSpec::seq_t my_seq = [](size_t idx){ return (5.0 - idx); };
    EXPECT_EQ(GenSpec().idx("a", 5).seq(my_seq).gen(), custom_vector);
}

//-----------------------------------------------------------------------------

TensorSpec basic_map = TensorSpec("tensor(a{})")
    .add({{"a", "0"}}, 1.0)
    .add({{"a", "1"}}, 2.0)
    .add({{"a", "2"}}, 3.0);

TensorSpec custom_map = TensorSpec("tensor(a{})")
    .add({{"a", "s0"}}, 1.0)
    .add({{"a", "s5"}}, 2.0)
    .add({{"a", "s10"}}, 3.0);

TEST(GenSpec, generating_basic_map) {
    EXPECT_EQ(GenSpec().map("a", 3).gen(), basic_map);
    EXPECT_EQ(GenSpec().map("a", 3, 1).gen(), basic_map);
    EXPECT_EQ(GenSpec().map("a", 3, 1, "").gen(), basic_map);
    EXPECT_EQ(GenSpec().map("a", {"0", "1", "2"}).gen(), basic_map);
}

TEST(GenSpec, generating_custom_map) {
    EXPECT_EQ(GenSpec().map("a", 3, 5, "s").gen(), custom_map);
    EXPECT_EQ(GenSpec().map("a", {"s0", "s5", "s10"}).gen(), custom_map);
}

//-----------------------------------------------------------------------------

TensorSpec basic_mixed = TensorSpec("tensor(a{},b[1],c{},d[3])")
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 0}}, 1.0)
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 1}}, 2.0)
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 2}}, 3.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 0}}, 4.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 1}}, 5.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 2}}, 6.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 0}}, 7.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 1}}, 8.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 2}}, 9.0);

TensorSpec inverted_mixed = TensorSpec("tensor(a{},b[1],c{},d[3])")
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 0}}, 1.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 0}}, 2.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 0}}, 3.0)
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 1}}, 4.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 1}}, 5.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 1}}, 6.0)
    .add({{"a", "0"},{"b", 0},{"c", "0"},{"d", 2}}, 7.0)
    .add({{"a", "1"},{"b", 0},{"c", "0"},{"d", 2}}, 8.0)
    .add({{"a", "2"},{"b", 0},{"c", "0"},{"d", 2}}, 9.0);

TEST(GenSpec, generating_basic_mixed) {
    EXPECT_EQ(GenSpec().map("a", 3).idx("b", 1).map("c", 1).idx("d", 3).gen(), basic_mixed);
}

TEST(GenSpec, generating_inverted_mixed) {
    EXPECT_EQ(GenSpec().idx("d", 3).map("c", 1).idx("b", 1).map("a", 3).gen(), inverted_mixed);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
