package org.rust.lang.core.cfg

interface RsControlFlow {
    val size: Int
    val instructions: List<RsInstruction>
    fun forEach(consumer: InstructionConsumer)
}

interface InstructionConsumer {
    fun accept(instruction: RsInstruction, index: Int)
}

class RsControlFlowImpl(override val instructions: List<RsInstruction>) : RsControlFlow {
    override val size: Int
        get() = instructions.size

//    override fun getElement(offset: Int): RsInstruction {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }

    override fun toString() = buildString {
        for ((index, instruction) in instructions.withIndex()) {
            if (this.isNotEmpty()) {
                append("\n")
            }
            append(index)
            append(" ")
            append(instruction)
        }
    }

    override fun forEach(consumer: InstructionConsumer) {
        for ((index, instruction) in instructions.withIndex()) {
            consumer.accept(instruction, index)
        }
    }
}

