# Credit: Caleb Norton (F23 cohort)
#!/usr/bin/env bash
#
# This script will run against all tests in the test directory. In summary:
# 1. find a testcase text file
# 2. run the ScannerTester on the file
# 3. take the output and compare (using diff) to the correct output example file
# 4. repeat as needed

# You'll need to change it to point it to the directory that you have testcases (and correct outputs) in 
# as well as adjust to how you run your compiled java i.e., Line 31 - I use maven to build and run

fail=0
echo "Running tests..."
# get folders in tests directory
folders=$(find tests -type d -maxdepth 1 -mindepth 1 -exec basename {} \;)

# for each folder
for folder in $folders; do
	echo "$folder"
	# get files in folder ending in .txt
	files=$(find tests/$folder -type f -name "*.txt" -exec basename {} \;)
	# sort the files
	files=$(echo $files | tr " " "\n" | sort | tr "\n" " ")
	# for each file
	for file in $files; do
		# trim the .txt
		file=${file%.txt}
		echo "Running $folder:$file"
		# run the file and compare to expected output
		mvn -q exec:java -Dexec.args="-s tests/$folder/$file.txt" | \
			diff --ignore-all-space - tests/$folder/$file.out > /dev/null
		# if the return code was not 0, then the test failed
		if [ $? -ne 0 ]; then
			echo "Test: $folder:$file failed"
			fail=1
		fi
	done
	echo
done
if [ $fail -eq 1 ]; then
	echo "Some tests failed"
	exit 1
else
	echo "All tests passed!!"
	exit 0
fi