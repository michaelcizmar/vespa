// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("compile-cpp_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/routing/route.h>

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("compile-cpp_test");
    mbus::Route r;
    TEST_DONE();
}
