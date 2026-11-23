package com.episodicreader.domain.lookup

/**
 * Curated exclusion lists for deterministic proper-name indexing (T-15 / BR-010).
 */
object EnglishStopwords {
    val STOPWORDS: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "nor", "for", "yet", "so",
        "in", "on", "at", "to", "from", "by", "with", "of", "into", "onto", "upon",
        "about", "above", "across", "after", "against", "along", "among", "around",
        "before", "behind", "below", "beneath", "beside", "between", "beyond", "during",
        "except", "inside", "near", "outside", "over", "since", "through", "toward",
        "towards", "under", "until", "within", "without",
        "i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself",
        "he", "him", "his", "himself", "she", "her", "hers", "herself",
        "it", "its", "itself", "we", "us", "our", "ours", "ourselves",
        "they", "them", "their", "theirs", "themselves",
        "this", "that", "these", "those", "here", "there", "where", "when", "while",
        "why", "how", "who", "whom", "whose", "which", "what", "whether",
        "all", "another", "any", "both", "each", "either", "every", "few", "many",
        "more", "most", "much", "neither", "no", "none", "nothing", "one", "other",
        "several", "some", "someone", "something", "such",
        "if", "then", "than", "though", "although", "because", "unless", "whereas",
        "however", "therefore", "meanwhile", "moreover", "furthermore", "still", "even",
        "now", "once", "soon", "suddenly", "perhaps", "maybe", "indeed", "truly",
        "really", "only", "just", "already", "always", "never", "often", "sometimes",
        "yes", "oh", "ah", "alas", "well", "please", "thank", "thanks", "hello", "goodbye",
        "come", "look", "listen", "wait", "see", "let", "give", "take", "tell", "say",
        "chapter", "part", "book", "section", "prologue", "epilogue", "contents", "preface",
        "page", "volume", "letter", "night", "morning", "evening", "afternoon", "day",
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
        "spring", "summer", "autumn", "fall", "winter",
    )

    val HONORIFICS: Set<String> = setOf(
        "mr", "mrs", "ms", "miss", "dr", "prof", "sir", "madam", "madame", "monsieur",
        "lady", "lord", "count", "countess", "prince", "princess", "duke", "duchess",
        "king", "queen", "captain", "major", "colonel", "general", "doctor", "professor",
        "father", "mother", "uncle", "aunt", "brother", "sister", "saint", "st",
    )
}
