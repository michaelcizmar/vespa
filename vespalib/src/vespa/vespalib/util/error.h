// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib {

std::string getErrorString(const int osError);

std::string getLastErrorString();

}
