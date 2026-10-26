package com.pragmaapps.keepintouch.domain.voice

/** How the assistant addresses the user: "sir", "ma'am", or neither. */
enum class Honorific { SIR, MAAM, NEUTRAL }

/**
 * Picks sir/ma'am from the first name the user gives on their very first conversation - so the
 * assistant never has to ask outright. Deliberately conservative: only names that are clearly
 * and commonly one or the other count; anything unknown, short, or used by everyone (Kiran,
 * Jyoti, Shashi, Santosh...) gets [Honorific.NEUTRAL] rather than a guess. A wrong guess is
 * always one sentence away from fixed ("main madam hoon") - see VoiceConversation.
 *
 * The name itself is never stored; only the resulting honorific is.
 */
object HonorificGuesser {

    private val MALE = keys(
        "aarav", "abhay", "abhinav", "abhishek", "aditya", "ajay", "akash", "akhil", "akshay", "alok", "aman", "amar",
        "amit", "amitabh", "anand", "anil", "ankit", "ankur", "anuj", "anurag", "arjun", "arun", "arvind", "ashish",
        "ashok", "ashwin", "atul", "ayush", "bharat", "deepak", "dev", "dhruv", "dinesh", "gaurav", "girish", "gopal",
        "govind", "harish", "harsh", "hemant", "himanshu", "ishaan", "jatin", "jay", "jitendra", "kabir", "kailash",
        "kamal", "karan", "kartik", "kishore", "kunal", "lakshman", "lalit", "madhav", "mahesh", "manish",
        "manoj", "mayank", "mohan", "mohit", "mukesh", "naveen", "nikhil", "nilesh", "nitin", "nitesh",
        "pankaj", "paras", "pawan", "piyush", "pradeep", "prakash", "pramod", "prashant", "pratik", "praveen",
        "rahul", "raj", "rajat", "rajesh", "rajiv", "rakesh", "ram", "ramesh", "ravi", "rishabh", "rohan", "rohit",
        "sachin", "sahil", "sameer", "sandeep", "sanjay", "saurabh", "shankar", "shivam", "shubham", "siddharth",
        "subhash", "sudhir", "sumit", "sunil", "suraj", "suresh", "tarun", "tushar", "uday", "umesh", "varun",
        "vijay", "vikas", "vikram", "vinay", "vinod", "vipin", "vishal", "vivek", "yash", "yogesh",
        "राहुल", "अमित", "राज", "रवि", "सुरेश", "रमेश", "विजय", "अजय", "संजय", "कुणाल", "कुनाल", "अमर", "मनोज",
        "राकेश", "दीपक", "विकास", "रोहित", "सचिन", "अर्जुन", "विशाल", "आकाश", "गौरव", "नितिन", "प्रकाश"
    )

    private val FEMALE = keys(
        "aarti", "aditi", "aishwarya", "akanksha", "amrita", "anamika", "anita", "anjali", "ankita", "anupama",
        "anushka", "aparna", "archana", "asha", "bhavna", "chandni", "deepa", "deepika", "divya", "durga", "gauri",
        "geeta", "gita", "hema", "isha", "jaya", "kajal", "kalpana", "kamla", "kavita", "kavya", "khushi", "komal",
        "kritika", "lakshmi", "lata", "mamta", "manisha", "meena", "meera", "megha", "mona", "nandini",
        "neelam", "neha", "nidhi", "nikita", "nisha", "pallavi", "pinki", "pooja", "poonam", "prachi", "pragya",
        "preeti", "priya", "priyanka", "radha", "rani", "rashmi", "reena", "rekha", "renu", "riya", "ritu", "ruchi",
        "sakshi", "sangeeta", "sapna", "sarita", "seema", "shalini", "shikha", "shilpa", "shreya", "shweta",
        "simran", "sneha", "sonal", "sonam", "sonia", "sudha", "sunita", "sushma", "swati", "tanvi",
        "tanya", "usha", "vandana", "vidya", "yamini",
        "प्रिया", "पूजा", "नेहा", "अंजलि", "सुनीता", "अनीता", "रेखा", "सीमा", "गीता", "कविता", "दिव्या", "स्वाति",
        "श्वेता", "प्रियंका", "निशा", "मीना", "रीना", "सपना", "ममता", "आरती", "लक्ष्मी"
    )

    private fun keys(vararg names: String): Set<String> = names.map { PhoneticKey.of(it) }.toSet()

    /** [nameWords] = what's left of "mera naam Rahul hai" after the filler - only the first word counts. */
    fun guess(nameWords: List<String>): Honorific {
        val first = nameWords.firstOrNull()?.let { PhoneticKey.of(it) }?.takeIf { it.length >= 3 } ?: return Honorific.NEUTRAL
        val male = first in MALE
        val female = first in FEMALE
        return when {
            male && !female -> Honorific.SIR
            female && !male -> Honorific.MAAM
            else -> Honorific.NEUTRAL
        }
    }
}
