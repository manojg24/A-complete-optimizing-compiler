# PA3 Submission

## Code

PA3 code must be submitted to **Gradescope** by  **12:00am on 10/09** (as mentioned in the syllabus, **deadlines in Gradescope take precedence** over all others listed in other course documentation)

### Files to Submit

* `ast/*.java`
* `mocha/*.java`
* `types/*.java`

Do not change package declaration, i.e., keep the first line of all `*.java` files as-is

### Grading

* **Total points: 160**
  * **Type checking:** 80 pts
    * Public: 16 pts (20%)
    * Private: 64 pts (80%)
  * **Interpreter:** 50 pts
    * Public: 10 pts (20%)
    * Private: 40 pts (80%)
  * **Symbol errors:** 30 pts (100% private)
* **Requirements for full credit on private tests**
  * **CSCE 434:** **10/14** yype checking tests passing, **10/14** interpreter tests passing, **3/5** symbol error tests passing
  * **CSCE 605:** **12/14** yype checking tests passing, **12/14** interpreter tests passing, **4/5** symbol error tests passing
* PA3 allows late submission. However the penalty for late code submission is *h<sup>2</sup>/24 + h*, where h is the whole number of hours late rounded up
* PA3 is fully autograded through Gradscope, but students may submit regrade requests if they believe that points were improperly deducted (see syllabus for limitations)


## Testcase

You must submit at least **two** tests to the **shared GitHub repository** by **12:00am on 10/06**:

* At least **one type-checking test** under `PA3/typechecking/`
* At least **one interpreter test** under `PA3/interpreter/`

### Naming Convention

* code file: `./PA3/test_F25_YY_description.txt` : `YY` is the test number; test numbering starts from whatever my tests end at and must be consecutive; the description is an optional short text
* input file: `./PA3/test_F25_YY.in` : input for testcase
* output file: `./PA3/test_F25_YY.out` the output (solution). You are expected to provide the solution. However, make sure this is not hand-generated.

### Header Format

* first line: name of the author
* second line: a brief description of the test case, and if necessary, what is expected

```
// <author>
// <testcase-description>
```

### Grading

* 5% penalty if either test is duplicate, late, or inconsistent; applied at any time after the PA deadline.
* 24 hrs before the PA deadline is the cutoff for test submission -> if you don't make it by then, there will be a no-submission penalty of 17%

## Suggestions

* Gradescope is not your debugger; you should be testing/debugging locally
* The PAs can be time-consuming, so begin early; **course staff will not monitor Campuswire within 12 hours of the assignment deadline** to encourage students to **seek help early**
* **Before contacting course staff regarding assigment-related questions, first consult both the syllabus and project description**