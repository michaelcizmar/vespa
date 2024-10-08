// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace search::fef {

/**
 * An object of this class may be used to build feature names in a
 * convenient way. Using this class will ensure things like correct
 * quoting of reserved characters used in parameters.
 **/
class FeatureNameBuilder
{
private:
    std::string              _baseName;
    std::vector<std::string> _parameters;
    std::string              _output;

public:
    /**
     * Create an empty builder.
     **/
    FeatureNameBuilder();
    ~FeatureNameBuilder();

    /**
     * Set the base name.
     *
     * @return this object, for chaining
     * @param str base name
     **/
    FeatureNameBuilder &baseName(const std::string &str);

    /**
     * Add a parameter to the end of the parameter list.
     *
     * @return this object, for chaining
     * @param str a parameter
     * @param exact if this is true, the parameter will preserve its
     * exact string value. If this is false, the framework is allowed
     * to normalize the string as if it was a feature name.
     **/
    FeatureNameBuilder &parameter(const std::string &str, bool exact = true);

    /**
     * Clear the list of parameters.
     *
     * @return this object, for chaining
     **/
    FeatureNameBuilder &clearParameters();

    /**
     * Set the output name
     *
     * @return this object, for chaining
     * @param str output name
     **/
    FeatureNameBuilder &output(const std::string &str);

    /**
     * Build a full feature name from the information put into this
     * object.
     *
     * @return feature name
     **/
    std::string buildName() const;
};

}
