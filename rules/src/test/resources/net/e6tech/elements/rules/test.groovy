exec("classpath://net/e6tech/elements/rules/test2.groovy")

rule ("rule 1.1") {
    description = "Sample test rule"

    condition {
        true
    }

    proceed {
        println ruleName()
        result['result'] = 'blah'
        println result['result']
    }

    rule("rule 1.1.1") {

        proceed {
            println ruleName()
        }
    }
}

rule ("rule 1.2") {
    condition {
        true
    }

    proceed {
        println ruleName()
        println(a)
    }
}

rule ("rule 1.2.1") {
    condition {
        true
    }

    proceed {
        println ruleName()
    }
}

rule ("rule 1.2.2") {
    condition {
        true
    }

    proceed {
        println ruleName()
    }
}

rule ("rule 1.3") {
    condition {
        true
    }

    proceed {
        println ruleName()
        println result['result']
    }
}

root ("test") {
    '''
    "rule 1.1" :
        - "rule 1.2" :
            - "rule 1.2.1"
            - "rule 1.2.2"
        - "rule 1.3"
        - "rule 1.4"
'''
}