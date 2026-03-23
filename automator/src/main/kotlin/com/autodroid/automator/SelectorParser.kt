package com.autodroid.automator

import com.autodroid.automator.filter.*
import org.json.JSONObject

/**
 * Parses JSON selector options from Node.js into a UiGlobalSelector.
 */
object SelectorParser {

    fun parse(json: String): UiGlobalSelector {
        val obj = JSONObject(json)
        val selector = UiGlobalSelector()

        obj.optString("text").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(TextFilter(TextFilter.Mode.EQUALS, it))
        }
        obj.optString("textContains").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(TextFilter(TextFilter.Mode.CONTAINS, it))
        }
        obj.optString("textStartsWith").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(TextFilter(TextFilter.Mode.STARTS_WITH, it))
        }
        obj.optString("textMatches").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(RegexFilter(RegexFilter.Property.TEXT, it))
        }
        obj.optString("id").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(IdFilter(it))
        }
        obj.optString("className").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(ClassNameFilter(it))
        }
        obj.optString("desc").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(DescFilter(DescFilter.Mode.EQUALS, it))
        }
        obj.optString("descContains").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(DescFilter(DescFilter.Mode.CONTAINS, it))
        }
        obj.optString("packageName").takeIf { it.isNotEmpty() }?.let {
            selector.addFilter(PackageNameFilter(it))
        }

        if (obj.has("clickable")) {
            selector.addFilter(BooleanFilter(BooleanFilter.Property.CLICKABLE, obj.getBoolean("clickable")))
        }
        if (obj.has("scrollable")) {
            selector.addFilter(BooleanFilter(BooleanFilter.Property.SCROLLABLE, obj.getBoolean("scrollable")))
        }
        if (obj.has("enabled")) {
            selector.addFilter(BooleanFilter(BooleanFilter.Property.ENABLED, obj.getBoolean("enabled")))
        }
        if (obj.has("depth")) {
            selector.addFilter(DepthFilter(obj.getInt("depth")))
        }

        obj.optString("algorithm").takeIf { it.isNotEmpty() }?.let {
            selector.algorithm(it)
        }

        return selector
    }
}
