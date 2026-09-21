package com.thread.demo

/**
 * The form, held in memory for the length of the demo.
 *
 * Deliberately not persisted. The point being made on stage is that the *app*
 * remembers the data perfectly well - what nobody remembers is the person's
 * place in the task. Draft-saving is not the problem Thread solves.
 */
object DemoState {

    var purpose: String = ""
    var destination: String = ""
    var travelDates: String = ""
    var travelClass: String = ""
    var estimatedCost: String = ""
    var approver: String = ""

    var costCentre: String = ""
    var budgetCode: String = ""
    var glAccount: String = ""
    var apportionment: String = ""
    var percentageSplit: String = ""
    var wbsElement: String = ""
    var attachments: String = ""

    fun reset() {
        purpose = ""
        destination = ""
        travelDates = ""
        travelClass = ""
        estimatedCost = ""
        approver = ""
        costCentre = ""
        budgetCode = ""
        glAccount = ""
        apportionment = ""
        percentageSplit = ""
        wbsElement = ""
        attachments = ""
    }

    /**
     * Travel classes. Three options, plainly named, no policy jargon.
     *
     * Part of keeping step 1 a *well-built* form while making it a longer one.
     * The load on this screen should come from how much there is to hold, not
     * from anything being unclear - otherwise the demo is arguing against a
     * form nobody would ship.
     */
    val travelClasses: List<String> = listOf(
        "Economy",
        "Premium economy",
        "Business",
    )

    /**
     * Twenty-five cost centres, unsorted, no search, no default.
     *
     * This is not a strawman. It is what an internal picker looks like when it
     * is generated straight from a finance system, and by Hick's Law it is
     * roughly a five-bit decision sitting in the middle of a form - which is
     * exactly where the design-time score flags it.
     */
    val costCentres: List<String> = listOf(
        "CC-1042 Corporate Services",
        "CC-1043 Corporate Services (Shared)",
        "CC-1101 Engineering - Platform",
        "CC-1102 Engineering - Devices",
        "CC-1103 Engineering - Cloud",
        "CC-1104 Engineering - Security",
        "CC-1201 Sales - EMEA",
        "CC-1202 Sales - AMER",
        "CC-1203 Sales - APAC",
        "CC-1204 Sales - India",
        "CC-1301 Marketing - Brand",
        "CC-1302 Marketing - Field",
        "CC-1303 Marketing - Digital",
        "CC-1401 Finance - Controllership",
        "CC-1402 Finance - Treasury",
        "CC-1501 Legal - Commercial",
        "CC-1502 Legal - Compliance",
        "CC-1601 People - Talent",
        "CC-1602 People - Benefits",
        "CC-1701 Facilities - Campus",
        "CC-1702 Facilities - Real Estate",
        "CC-1801 Support - Tier 1",
        "CC-1802 Support - Tier 2",
        "CC-1901 Research - Applied",
        "CC-1902 Research - Fundamental",
    )

    val apportionmentOptions: List<String> = listOf(
        "Pro-rata by headcount",
        "Pro-rata by revenue",
        "Fixed percentage",
        "Direct attribution",
    )

    /**
     * The choice that makes two more fields appear.
     *
     * Conditional fields are the part of enterprise forms that people describe
     * as the form "changing under them": the work left is not knowable from
     * looking at the screen, because answering one question adds two more. That
     * is what makes a step count worth showing, and it is deterministic - the
     * same answer always produces the same form, so the demo cannot drift.
     */
    const val SPLIT_TRIGGER = "Fixed percentage"

    fun requiresSplit(): Boolean = apportionment == SPLIT_TRIGGER
}
