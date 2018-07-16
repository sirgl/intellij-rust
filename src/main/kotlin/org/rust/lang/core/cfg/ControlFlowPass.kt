package org.rust.lang.core.cfg

import com.intellij.util.containers.IntStack
import java.util.*

fun <T> RsControlFlow.bypass(visitor: RsResultControlFlowVisitor<T>) : T? {
    val passed = BitSet(instructions.size)
    var ip = 0
    val bypassStack = IntStack()
    while (true) {
        if (passed[ip]) {
            if (bypassStack.empty()) return visitor.result
            ip = bypassStack.pop()
        }
        val currentInstruction = instructions[ip]
        currentInstruction.accept(visitor, ip)
        if (!visitor.shouldContinueBypass()) return visitor.result
        if (currentInstruction is GoToInstruction) {
            bypassStack.push(currentInstruction.targetIndex)
        }
        if (currentInstruction is DivergingInstruction) {
            if (bypassStack.empty()) return visitor.result
            ip = bypassStack.pop()
            continue
        }
        passed[ip] = true
        ip++
    }
}
