# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_feedtoken_test_app TEST
    SOURCES
    feedtoken_test.cpp
    DEPENDS
    searchcore_pcommon
    searchcore_proton_metrics
    GTest::gtest
)
vespa_add_test(NAME searchcore_feedtoken_test_app COMMAND searchcore_feedtoken_test_app)
