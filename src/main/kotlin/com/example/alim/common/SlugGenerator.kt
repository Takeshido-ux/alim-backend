package com.example.alim.common

object SlugGenerator {
	fun fromTitle(title: String): String {
		val transliterated = title.trim().lowercase().map { char ->
			TRANSLIT[char] ?: char
		}.joinToString("")

		val slug = transliterated
			.replace(Regex("[^a-z0-9]+"), "_")
			.trim('_')
			.replace(Regex("_+"), "_")

		return slug.ifBlank { "item" }
	}

	fun unique(base: String, exists: (String) -> Boolean): String {
		if (!exists(base)) {
			return base
		}
		var index = 2
		while (exists("${base}_$index")) {
			index += 1
		}
		return "${base}_$index"
	}

	private val TRANSLIT = mapOf(
		'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
		'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
		'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
		'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
		'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
		'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
		'э' to "e", 'ю' to "yu", 'я' to "ya",
	)
}
