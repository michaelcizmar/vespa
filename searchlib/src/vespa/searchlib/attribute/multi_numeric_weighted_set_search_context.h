// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"
#include "multi_value_mapping.h"
#include "numeric_range_matcher.h"

namespace search::attribute {

/*
 * MultiNumericWeightedSearchContext handles the creation of search iterators for
 * a query term on a multi value numeric weighted set attribute vector.
 */
template <typename T, typename M>
class MultiNumericWeightedSetSearchContext final : public NumericRangeMatcher<T>, public SearchContext
{
private:
    const MultiValueMapping<M>& _mv_mapping;

    int32_t onFind(DocId docId, int32_t elemId, int32_t& weight) const override {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int32_t elemId) const override {
        return find(docId, elemId);
    }

    bool valid() const override;

public:
    MultiNumericWeightedSetSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping);

    Int64Range getAsIntegerTerm() const override;

    int32_t find(DocId doc, int32_t elemId, int32_t & weight) const {
        auto values(_mv_mapping.get(doc));
        for (uint32_t i(elemId); i < values.size(); i++) {
            if (this->match(values[i].value())) {
                weight = values[i].weight();
                return i;
            }
        }
        return -1;
    }

    int32_t find(DocId doc, int32_t elemId) const {
        auto values(_mv_mapping.get(doc));
        for (uint32_t i(elemId); i < values.size(); i++) {
            if (this->match(values[i].value())) {
                return i;
            }
        }
        return -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
};

}
