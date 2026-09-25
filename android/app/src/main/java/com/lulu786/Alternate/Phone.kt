package com.lulu786.Alternate

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

class Country(val iso: String, val dial: String) {
    @Suppress("DEPRECATION")
    val name: String get() = Locale("", iso).displayCountry
    val flag: String
        get() = String(Character.toChars(0x1F1E6 + iso[0].code - 'A'.code)) +
            String(Character.toChars(0x1F1E6 + iso[1].code - 'A'.code))
}

/** A phone number split into its country and the national part (digits only). */
class ParsedNumber(val country: Country, val national: String) {
    val full: String get() = country.dial + national
}

object Phone {
    // ISO code + dial code. Where several countries share a dial code the preferred one comes first.
    private const val TABLE =
        "AF93,AL355,DZ213,AD376,AO244,AR54,AM374,AU61,AT43,AZ994,BS1242,BH973,BD880,BB1246,BY375,BE32," +
            "BZ501,BJ229,BT975,BO591,BA387,BW267,BR55,BN673,BG359,BF226,BI257,KH855,CM237,US1,CA1,CV238," +
            "CF236,TD235,CL56,CN86,CO57,KM269,CG242,CR506,HR385,CU53,CY357,CZ420,DK45,DJ253,DM1767,DO1809," +
            "EC593,EG20,SV503,GQ240,ER291,EE372,ET251,FJ679,FI358,FR33,GA241,GM220,GE995,DE49,GH233,GR30," +
            "GD1473,GT502,GN224,GW245,GY592,HT509,HN504,HU36,IS354,IN91,ID62,IR98,IQ964,IE353,IL972,IT39," +
            "CI225,JM1876,JP81,JO962,RU7,KZ7,KE254,KI686,KP850,KW965,KG996,LA856,LV371,LB961,LS266,LR231," +
            "LY218,LI423,LT370,LU352,MG261,MW265,MY60,MV960,ML223,MT356,MH692,MR222,MU230,MX52,FM691,MD373," +
            "MC377,MN976,ME382,MA212,MZ258,MM95,NA264,NR674,NP977,NL31,NZ64,NI505,NE227,NG234,MK389,NO47," +
            "OM968,PK92,PW680,PA507,PG675,PY595,PE51,PH63,PL48,PT351,PR1787,PR1939,QA974,RO40,RW250,KN1869," +
            "LC1758,VC1784,WS685,SM378,ST239,SA966,SN221,RS381,SC248,SL232,SG65,SK421,SI386,SB677,SO252,ZA27," +
            "KR82,SS211,ES34,LK94,SD249,SR597,SZ268,SE46,CH41,SY963,TW886,TJ992,TZ255,TH66,TL670,TG228," +
            "TO676,TT1868,TN216,TR90,TM993,TV688,UG256,UA380,AE971,GB44,UY598,UZ998,VU678,VA39,VE58,VN84," +
            "YE967,ZM260,ZW263"

    val countries: List<Country> by lazy {
        TABLE.split(',').map { Country(it.substring(0, 2), it.substring(2)) }
    }

    /** Platform libphonenumber (validating); replaceable in JVM tests where the framework can't load. */
    var e164: (String, String) -> String? = { n, iso -> runCatching { PhoneNumberUtils.formatNumberToE164(n, iso) }.getOrNull() }

    fun byIso(iso: String?): Country? = countries.firstOrNull { it.iso == iso?.uppercase() }

    /** Longest dial code that prefixes [digits], preferring [current] on ties. */
    private fun byDialPrefix(digits: String, current: Country?): Country? {
        val matches = countries.filter { digits.startsWith(it.dial) }
        val len = matches.maxOfOrNull { it.dial.length } ?: return null
        val best = matches.filter { it.dial.length == len }
        return best.firstOrNull { it.iso == current?.iso } ?: best.first()
    }

    /**
     * Keeps only digits (any script) and a leading '+'. Numbers copied from dialers usually contain
     * spaces, dashes, brackets, non-breaking spaces or invisible direction marks (U+202A/U+202C).
     */
    fun clean(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            val d = Character.digit(ch, 10)
            if (d >= 0) sb.append(('0' + d))
            else if ((ch == '+' || ch == '＋') && sb.isEmpty()) sb.append('+')
        }
        return sb.toString()
    }

    /**
     * Parses whatever the user typed or pasted ("+91 98765 43210", "(202) 555-0123", "0044 20 7946 0958",
     * "098765 43210") into a country and national number. Returns null when there are no digits.
     */
    fun parse(raw: String, current: Country): ParsedNumber? {
        var s = clean(raw)
        if (s.startsWith("00")) s = "+" + s.substring(2)
        if (s.startsWith("+")) {
            val d = s.substring(1)
            val c = byDialPrefix(d, current) ?: return null
            val national = d.substring(c.dial.length)
            return if (national.isEmpty()) null else ParsedNumber(c, national)
        }
        if (s.isEmpty()) return null
        // Let the platform's libphonenumber strip trunk prefixes / a repeated country code.
        val intl = e164(s, current.iso)
        if (intl != null) {
            val d = intl.removePrefix("+")
            if (d.startsWith(current.dial) && d.length > current.dial.length) {
                return ParsedNumber(current, d.substring(current.dial.length))
            }
        }
        if (s.length > 1 && s[0] == '0' && current.iso != "IT") s = s.substring(1)
        return ParsedNumber(current, s)
    }

    /** Keys to look up an incoming/outgoing call number in the database. */
    fun lookupKeys(ctx: Context, raw: String): List<String> {
        val iso = dialCountry(ctx)
        val digits = clean(raw)
        val keys = LinkedHashSet<String>()
        e164(digits, iso)?.let { keys.add(it.removePrefix("+")) }
        keys.add(
            when {
                digits.startsWith("+") -> digits.substring(1)
                digits.startsWith("0") && iso != "IT" -> digits.substring(1)
                else -> digits
            }
        )
        return keys.filter { it.isNotEmpty() }
    }

    fun format(c: Contact): String =
        runCatching { PhoneNumberUtils.formatNumber("+" + c.fullPhoneNumber, c.countryCode) }.getOrNull()
            ?: "+${c.fullPhoneNumber}"

    /** Network country, then SIM, then locale, then India (same order as before). */
    fun dialCountry(ctx: Context): String {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return listOf(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            ctx.resources.configuration.locales.get(0)?.country,
        ).firstOrNull { !it.isNullOrEmpty() && byIso(it) != null }?.uppercase() ?: "IN"
    }
}
