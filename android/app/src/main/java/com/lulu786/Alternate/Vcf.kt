package com.lulu786.Alternate

/** vCard 2.1 export/import compatible with Google Contacts (same format as the previous app). */
object Vcf {
    private fun esc(v: String) =
        v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n").replace("\r", "")

    private fun unesc(v: String) =
        v.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    fun write(contacts: List<Contact>): String = contacts.joinToString("\r\n\r\n") { c ->
        val lines = mutableListOf(
            "BEGIN:VCARD",
            "VERSION:2.1",
            "N:;${c.name};;${esc(c.prefix)};${esc(c.suffix)}",
            "FN:${esc(c.displayName)}",
        )
        if (c.nickname.isNotEmpty()) lines += "X-ANDROID-CUSTOM:vnd.android.cursor.item/nickname;${esc(c.nickname)};1;;;;;;;;;;;;;"
        lines += "TEL;CELL;PREF:+${c.fullPhoneNumber}"
        if (c.email.isNotEmpty()) lines += "EMAIL;PREF;HOME:${esc(c.email)}"
        if (c.location.isNotEmpty()) lines += "ADR;PREF;HOME:;;;;${esc(c.location)};;"
        if (c.appointment.isNotEmpty()) lines += "TITLE:${esc(c.appointment)}"
        if (c.website.isNotEmpty()) lines += "URL:${esc(c.website)}"
        if (c.notes.isNotEmpty()) lines += "NOTE:${esc(c.notes)}"
        if (c.birthday.isNotEmpty()) lines += "BDAY:${esc(c.birthday)}"
        if (c.photo.isNotEmpty()) {
            val isUri = c.photo.startsWith("data:image/")
            val data = if (isUri) c.photo.substringAfter(',') else c.photo
            val mime = if (isUri) c.photo.substringBefore(';').removePrefix("data:") else "image/jpeg"
            lines += "PHOTO;ENCODING=BASE64;TYPE=${mime.uppercase()}:$data"
        }
        lines += "END:VCARD"
        lines.joinToString("\r\n")
    }

    fun read(content: String, defaultCountry: Country): List<Contact> {
        val text = content.replace(Regex("\r\n?"), "\n").replace(Regex("\n[ \t]"), "")
        val out = ArrayList<Contact>()
        for (card in text.split("BEGIN:VCARD")) {
            if (card.isBlank()) continue
            var c = Contact("", "", "", "")
            for (line in card.split('\n')) {
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val field = line.substring(0, colon)
                val value = unesc(line.substring(colon + 1))
                val upper = field.uppercase()
                when {
                    upper == "N" -> {
                        val p = value.split(';')
                        val name = listOf(p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrElse(0) { "" })
                            .filter { it.isNotEmpty() }.joinToString(" ")
                        c = c.copy(prefix = p.getOrElse(3) { "" }, suffix = p.getOrElse(4) { "" })
                        if (name.isNotEmpty()) c = c.copy(name = name)
                    }
                    upper == "FN" && c.name.isEmpty() -> c = c.copy(name = value.trim())
                    upper.startsWith("TEL") && c.fullPhoneNumber.isEmpty() ->
                        Phone.parse(value, defaultCountry)?.let {
                            c = c.copy(fullPhoneNumber = it.full, phoneNumber = it.national, countryCode = it.country.iso)
                        }
                    upper.startsWith("EMAIL") && c.email.isEmpty() && upper.contains("PREF") -> c = c.copy(email = value)
                    upper.startsWith("ADR") && upper.contains("PREF") -> {
                        val p = value.split(';')
                        c = c.copy(location = p.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: p.getOrElse(3) { "" })
                    }
                    upper == "TITLE" -> c = c.copy(appointment = value)
                    upper == "URL" -> c = c.copy(website = value)
                    upper == "NOTE" -> c = c.copy(notes = value)
                    upper == "BDAY" ->
                        if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}|--\\d{2}-\\d{2}"))) c = c.copy(birthday = value)
                    upper == "X-ANDROID-CUSTOM" && value.startsWith("vnd.android.cursor.item/nickname;") ->
                        c = c.copy(nickname = value.split(';').getOrElse(1) { "" })
                    upper.startsWith("PHOTO") && upper.contains("BASE64") -> {
                        val mime = Regex("TYPE=([^;:]+)").find(upper)?.groupValues?.get(1)?.lowercase()?.removePrefix("image/") ?: "jpeg"
                        c = c.copy(photo = "data:image/$mime;base64,${value.replace(Regex("\\s"), "")}")
                    }
                }
            }
            if (c.name.isNotEmpty() && c.fullPhoneNumber.isNotEmpty()) out += c
        }
        return out
    }
}
