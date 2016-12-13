rule ("rule 1.4") {
    description = "Sample test rule"

    condition {
        true
    }

    proceed {
        println ruleName()
        result['result'] = 'blah'
        println result['result']
    }
}