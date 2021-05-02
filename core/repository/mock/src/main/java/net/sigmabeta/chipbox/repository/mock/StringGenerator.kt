package net.sigmabeta.chipbox.repository.mock

import java.util.*
import javax.inject.Inject

class StringGenerator @Inject constructor(private val random: Random) {
    fun generateName() = "${FIRST_NAMES[random.nextInt(FIRST_NAMES.size)]} " +
            LAST_NAMES[random.nextInt(LAST_NAMES.size)]

    fun generateTitle(): String {
        val wordCount = random.nextInt(MockRepository.MAX_WORDS_PER_TITLE - 1) + 1
        val builder = StringBuilder()

        for (wordIndex in 0 until wordCount) {
            val randomWordIndex = random.nextInt(RANDOM_WORDS.size - 1)
            val randomWord = RANDOM_WORDS[randomWordIndex]
            builder.append(randomWord)
            builder.append(' ')
        }

        return builder.toString().trim()
    }

    companion object {
        val RANDOM_WORDS = """Lorem ipsum dolor sit amet consectetur adipiscing elit Curabitur 
                |iaculis neque vel fermentum dictum Pellentesque ac justo ultricies 
                |hendrerit sem in blandit tellus Nam non congue ante In ultricies 
                |hendrerit velit at lobortis velit sodales eget Quisque quis pellentesque 
                |urna Suspendisse consequat ut tellus in sollicitudin Aliquam facilisis a 
                |justo quis iaculis Donec feugiat pharetra orci in iaculis metus tincidunt 
                |quis In eget ligula leo Integer finibus metus ut est molestie et finibus 
                |ligula convallis"""
            .trimMargin()
            .split(" ")
            .map { it.trim() }
            .map { it.capitalize(Locale.getDefault()) }
            .toList()

        val FIRST_NAMES = listOf(
            "Exie",
            "Euna",
            "Kimiko",
            "Robbin",
            "Maximina",
            "Elias",
            "Maryellen",
            "Marcene",
            "Phung",
            "Karoline",
            "Rosie",
            "Branden",
            "Eryn",
            "Kurt",
            "Cristopher",
            "Tien",
            "Kori",
            "Eldon",
            "Jeannetta",
            "Cinda",
            "William",
            "Alona",
            "Tommy",
            "Tawana",
            "Nevada",
            "Terrell",
            "Camille",
            "Albertine",
            "Tristan",
            "Joye",
            "Amber",
            "Laree",
            "Kali",
            "Lacresha",
            "Dione",
            "Paul",
            "Myung",
            "Trista",
            "Epifania",
            "Michell",
            "Odessa",
            "Columbus",
            "Emerita",
            "Rosann",
            "Ethelene",
            "Domitila",
            "Lawanda",
            "Jamaal",
            "Hilario",
            "Liane"
        )

        val LAST_NAMES = listOf(
            "Clayton",
            "Crane",
            "Browning",
            "Conrad",
            "Joseph",
            "Hanson",
            "Donovan",
            "Rice",
            "Green",
            "Nixon",
            "Hoffman",
            "Haley",
            "Kelly",
            "Powell",
            "Costa",
            "Blackburn",
            "Anthony",
            "Gutierrez",
            "Mcintosh",
            "Bolton",
            "Maynard",
            "Pratt",
            "Conley",
            "Blackwell",
            "Mullen",
            "Simpson",
            "Collins",
            "Matthews",
            "English",
            "Chapman",
            "Frederick",
            "Montoya",
            "Campos",
            "Spencer",
            "Mccullough",
            "Rivas",
            "Weeks",
            "Quinn",
            "Morrison",
            "Franco",
            "Moran",
            "Allen",
            "Good",
            "Benitez",
            "Haas",
            "Wyatt",
            "Dunn",
            "Davila",
            "Harrison",
            "Wallace"
        )
    }
}
