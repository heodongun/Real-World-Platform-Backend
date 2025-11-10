package com.codingplatform.services

import com.codingplatform.models.ExecutionResult
import com.codingplatform.models.CoverageReport
import com.codingplatform.models.TestResult
import com.codingplatform.models.TestResults
import com.codingplatform.models.TestStatus
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class TestRunnerService {

    fun parseTestResults(executionResult: ExecutionResult): TestResults {
        val testCases = parseJUnitXml(executionResult.output)
        val passed = testCases.count { it.status == TestStatus.PASSED }
        val failed = testCases.count { it.status == TestStatus.FAILED }
        val coverage = extractCoverage(executionResult.output)

        return TestResults(
            passed = passed,
            failed = failed,
            total = testCases.size,
            details = testCases,
            coverage = coverage
        )
    }

    private fun parseJUnitXml(output: String): List<TestResult> {
        val xmlStart = output.indexOf("<?xml")
        if (xmlStart == -1) return parseFromLogs(output)

        val xmlContent = output.substring(xmlStart)
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(ByteArrayInputStream(xmlContent.toByteArray()))
            val nodes = document.getElementsByTagName("testcase")
            buildList {
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val name = node.attributes?.getNamedItem("name")?.nodeValue ?: "Unknown"
                    val time = node.attributes?.getNamedItem("time")?.nodeValue?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
                    var status = TestStatus.PASSED
                    var error: String? = null
                    val childCount = node.childNodes.length
                    for (j in 0 until childCount) {
                        val child = node.childNodes.item(j)
                        if (child.nodeType == Node.ELEMENT_NODE &&
                            (child.nodeName == "failure" || child.nodeName == "error")
                        ) {
                            status = TestStatus.FAILED
                            error = child.textContent
                            break
                        }
                    }
                    add(TestResult(testId = i.toString(), name = name, status = status, error = error, duration = time))
                }
            }
        } catch (ex: Exception) {
            parseFromLogs(output)
        }
    }

    private fun parseFromLogs(output: String): List<TestResult> {
        var counter = 0
        return output.lines().mapNotNull { line ->
            when {
                line.contains("PASSED", ignoreCase = true) -> {
                    TestResult(
                        testId = (counter++).toString(),
                        name = extractTestName(line),
                        status = TestStatus.PASSED,
                        error = null,
                        duration = 0
                    )
                }

                line.contains("FAILED", ignoreCase = true) -> {
                    TestResult(
                        testId = (counter++).toString(),
                        name = extractTestName(line),
                        status = TestStatus.FAILED,
                        error = "Test failed. See logs for detail.",
                        duration = 0
                    )
                }

                else -> null
            }
        }
    }

    private fun extractTestName(line: String): String {
        val regex = """Test\s+([A-Za-z0-9_.$]+)""".toRegex()
        return regex.find(line)?.groupValues?.getOrNull(1) ?: "UnknownTest"
    }

    private fun extractCoverage(output: String): CoverageReport {
        val lineRegex = """Line coverage:\s*(\d+)%""".toRegex()
        val branchRegex = """Branch coverage:\s*(\d+)%""".toRegex()
        val line = lineRegex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val branch = branchRegex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return CoverageReport(line = line, branch = branch, uncoveredLines = emptyList())
    }
}
