package com.jediterm.terminal.model.hyperlinks

import java.util.List

/**
 * @author traff
 */
class LinkResult(val items: MutableList<LinkResultItem?>) {
    constructor(item: LinkResultItem) : this(List.of<LinkResultItem?>(item))
}
