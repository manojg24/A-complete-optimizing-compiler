OPtimizing Compiler

Mocha is a small educational compiler built for a simplified C-like language.
It implements the full compilation pipeline used in university compiler courses (PA3–PA6):

✔ Full AST construction & type checking
✔ Three-Address-Code (TAC) IR generation
✔ Basic-block construction & CFG generation
✔ Classical compiler optimizations (CP, CSE, CF, DCE, etc.)
✔ Global Constant Propagation (GCP)
✔ Function Inlining
✔ Register Allocation (Graph Coloring)
✔ DLX Code Generation for a custom MIPS-like machine
✔ Execution using DLX simulator

This repo contains a working compiler that transforms Mocha source code into DLX machine instructions and executes them using the provided DLX interpreter.

⭐ Features
1. Front End
✔ Lexer & Parser

Parses a small, structured language with:
integer, float, and boolean types
arithmetic & boolean expressions
if / while / repeat loops
first-class functions
multidimensional arrays
return statements
built-in I/O operations

✔ Abstract Syntax Tree (AST)

A full AST hierarchy is implemented inside mocha/AST.java.

✔ Type Checking

Includes:
arithmetic type rules
float–int coercions
boolean expression validation
function parameter type checking
array indexing rules
return type validation

2. IR Generation (PA4)

The IR is a Three-Address-Code (TAC) in SSA-like form, structured into BasicBlocks.
The IR builder produces TAC for:

arithmetic ops (add, mul, sub, div, …)
boolean ops (and, or, not)
comparisons (cmplt, cmpeq, …)
branches (test)
function calls
return statements
The IR is fully printable as a DOT CFG for debugging.

3. Control-Flow Graph (CFG)

Each function and main is lowered into basic blocks with explicit:

predecessor lists
successor lists
branch structure
CFG tools include:
empty block merging
unreachable block elimination
branch simplification

4. Optimizations (PA5)
Implemented local optimizations

Each corresponds to a PA5 optimization flag:

Flag	Optimization
cp	Local copy propagation
cpp	Local constant propagation
cf	Control-flow simplification
cse	Local common subexpression elimination
dce	Dead-code elimination
cfg	CFG cleanup (remove empty / unreachable blocks)
merge	Merge trivial blocks
ofe	Remove orphan functions

All optimizations run on the basic-block level or block-local instruction list.

Global optimizations
These run automatically when any optimization flag (or -max) is used:
Global Constant Propagation (GCP) – eliminates global constant expressions across functions
Function Inlining – replaces small functions with their bodies
DCE cleanup after inlining
CFG simplification + block merging
-loop Fixpoint Mode

Repeats selected local optimizations until IR stops changing.

-max Mode

Runs the full recommended pipeline:
cp → cpp → cf → cse → dce → cfg → ofe → merge → GCP → inlining → DCE → CFG

5. Register Allocation (PA6)

A full Chaitin graph-coloring allocator, including:

live-variable analysis
interference graph construction
node simplification
spilling heuristic (highest-degree node)
rewriting TAC with machine registers (R1–R27)
memory locations for spilled values (M_x)
removal of silly mov R# → R#

6. DLX Code Generation

Translates TAC → DLX machine code:

Arithmetic
integer ops → ADD, SUB, MUL, DIV
float ops → fADD, fSUB, fMUL, fDIV
mixed constant handling
float literal lowering via FP16/FP32 packing
Comparisons
integer comparisons using subtract + branch
float comparisons using DLX fCMP
Memory & Spilled Variables
Each variable is assigned a negative offset from the global pointer R30.
Function Calls
user functions → JSR, return values in R27
built-in functions:
readInt, readBool, readFloat
printInt, printBool, printFloat, println
Control Flow
test lowered into BEQ/BNE jumps
correct PC-relative branch offsets
return in main → RET 0
return from function → RET 31

7. DLX Execution

The provided DLX simulator (DLX.java) executes:

integer and float arithmetic
boolean ops
branches
loads/stores
I/O
full runtime state printing (debug mode)


🛠 Build & Run
javac mocha/*.java
java mocha.CompilerTester program.mocha


Run with optimization:
java mocha.CompilerTester -o cp,cpp,cf,max program.mocha
