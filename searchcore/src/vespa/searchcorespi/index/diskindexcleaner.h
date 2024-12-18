// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace searchcorespi {
namespace index {
class DiskIndexes;

/**
 * Utility class used to clean and remove index directories.
 */
struct DiskIndexCleaner {
    /**
     * Deletes all indexes with id lower than the most recent fusion id.
     */
    static void clean(const std::string &index_dir,
                      DiskIndexes& disk_indexes);
    static void removeOldIndexes(const std::string &index_dir,
                                 DiskIndexes& disk_indexes);
};

}  // namespace index
}  // namespace searchcorespi

