from lldb import SBValue, SBError
from lldb.formatters import Logger


#################################################################################################################
# This file contains two kinds of pretty-printers: summary and synthetic.
#
# Important classes from LLDB module:
#   SBValue: the value of a variable, a register, or an expression
#   SBType:  the data type; each SBValue has a corresponding SBType
#
# Summary provider is a function with the type `(SBValue, dict) -> str`.
#   The first parameter is the object encapsulating the actual variable being displayed;
#   The second parameter is an internal support parameter used by LLDB, and you should not touch it.
#
# Synthetic children is the way to provide a children-based user-friendly representation of the object's value.
# Synthetic provider is a class that implements the following interface:
#
#     class SyntheticChildrenProvider:
#         def __init__(self, SBValue, dict)
#         def num_children(self)
#         def get_child_index(self, str)
#         def get_child_at_index(self, int)
#         def update(self)
#         def has_children(self)
#         def get_value(self)
#
#
# You can find more information and examples here:
#   1. https://lldb.llvm.org/varformats.html
#   2. https://lldb.llvm.org/python-reference.html
#   3. https://lldb.llvm.org/python_reference/lldb.formatters.cpp.libcxx-pysrc.html
#   4. https://github.com/llvm-mirror/lldb/tree/master/examples/summaries/cocoa
################################################################################################################


class DefaultSynthteticProvider:
    def __init__(self, valobj, dict):
        # type: (SBValue, dict) -> DefaultSynthteticProvider
        logger = Logger.Logger()
        logger >> "Default synthetic provider for " + str(valobj.GetName())
        self.valobj = valobj

    def num_children(self):
        # type: () -> int
        return self.valobj.GetNumChildren()

    def get_child_index(self, name):
        # type: (str) -> int
        return self.valobj.GetIndexOfChildWithName(name)

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        return self.valobj.GetChildAtIndex(index)

    def update(self):
        # type: () -> None
        pass

    def has_children(self):
        # type: () -> bool
        return self.valobj.MightHaveChildren()


class EmptySyntheticProvider:
    def __init__(self, valobj, dict):
        # type: (SBValue, dict) -> EmptySyntheticProvider
        logger = Logger.Logger()
        logger >> "[EmptySyntheticProvider] for " + str(valobj.GetName())
        self.valobj = valobj

    def num_children(self):
        # type: () -> int
        return 0

    def get_child_index(self, name):
        # type: (str) -> int
        return None

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        return None

    def update(self):
        # type: () -> None
        pass

    def has_children(self):
        # type: () -> bool
        return False


def SizeSummaryProvider(valobj, dict):
    # type: (SBValue, dict) -> str
    return 'size=' + str(valobj.GetNumChildren())


class StdVecSyntheticProvider:
    """Pretty-printer for alloc::vec::Vec<T>

    struct Vec<T> { buf: RawVec<T>, len: usize }
    struct RawVec<T> { ptr: Unique<T>, cap: usize, ... }
    struct Unique<T: ?Sized> { pointer: NonZero<*const T>, ... }
    struct NonZero<T>(T)
    """

    def __init__(self, valobj, dict):
        # type: (SBValue, dict) -> StdVecSyntheticProvider
        logger = Logger.Logger()
        logger >> "[StdVecSyntheticProvider] for " + str(valobj.GetName())
        self.valobj = valobj
        self.update()

    def num_children(self):
        # type: () -> int
        return self.length

    def get_child_index(self, name):
        # type: (str) -> int
        index = name.lstrip('[').rstrip(']')
        if index.isdigit():
            return int(index)
        else:
            return -1

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        start = self.data_ptr.GetValueAsUnsigned()
        address = start + index * self.element_type_size
        element = self.data_ptr.CreateValueFromAddress("[%s]" % index, address, self.element_type)
        return element

    def update(self):
        # type: () -> None
        self.length = self.valobj.GetChildMemberWithName("len").GetValueAsUnsigned()
        self.buf = self.valobj.GetChildMemberWithName("buf")
        self.data_ptr = self.buf.GetChildAtIndex(0).GetChildAtIndex(0).GetChildAtIndex(0)
        self.element_type = self.data_ptr.GetType().GetPointeeType()
        self.element_type_size = self.element_type.GetByteSize()

    def has_children(self):
        # type: () -> bool
        return True


def StdStringSummaryProvider(valobj, dict):
    # type: (SBValue, dict) -> str
    assert valobj.GetNumChildren() == 1
    logger = Logger.Logger()
    logger >> "[StdStringSummaryProvider] for " + str(valobj.GetName())

    vec = valobj.GetChildAtIndex(0)
    length = vec.GetNumChildren()
    chars = [chr(vec.GetChildAtIndex(i).GetValueAsUnsigned()) for i in range(length)]
    return '"%s"' % "".join(chars)


def StdStrSummaryProvider(valobj, dict):
    # type: (SBValue, dict) -> str
    assert valobj.GetNumChildren() == 2
    logger = Logger.Logger()
    logger >> "[StdStrSummaryProvider] for " + str(valobj.GetName())

    length = valobj.GetChildMemberWithName("length").GetValueAsUnsigned()
    data_ptr = valobj.GetChildMemberWithName("data_ptr")

    start = data_ptr.GetValueAsUnsigned()
    error = SBError()
    process = data_ptr.GetProcess()
    data = process.ReadMemory(start, length, error)
    if error.Success():
        return '"%s"' % data
    else:
        return '<error: %s>' % error.GetCString()


class StructSyntheticProvider:
    """Pretty-printer for structs and struct enum variants"""

    def __init__(self, valobj, dict, is_variant=False):
        # type: (SBValue, dict) -> StructSyntheticProvider
        logger = Logger.Logger()
        self.valobj = valobj
        self.is_variant = is_variant
        self.type = valobj.GetType()
        self.fields = {}

        if is_variant:
            logger >> "[StructVariantSyntheticProvider] for " + str(valobj.GetName())
            self.fields_count = self.type.GetNumberOfFields() - 1
            real_fields = self.type.fields[1:]
        else:
            logger >> "[StructSyntheticProvider] for " + str(valobj.GetName())
            self.fields_count = self.type.GetNumberOfFields()
            real_fields = self.type.fields

        for number, field in enumerate(real_fields):
            self.fields[field.name] = number

    def num_children(self):
        # type: () -> int
        return self.fields_count

    def get_child_index(self, name):
        # type: (str) -> int
        return self.fields.get(name, -1)

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        if self.is_variant:
            field = self.type.GetFieldAtIndex(index + 1)
        else:
            field = self.type.GetFieldAtIndex(index)
        return self.valobj.GetChildMemberWithName(field.name)

    def update(self):
        # type: () -> None
        pass

    def has_children(self):
        # type: () -> bool
        return True


class TupleSyntheticProvider:
    """Pretty-printer for tuples and tuple enum variants"""

    def __init__(self, valobj, dict, is_variant=False):
        # type: (SBValue, dict) -> TupleSyntheticProvider
        logger = Logger.Logger()
        self.valobj = valobj
        self.is_variant = is_variant
        self.type = valobj.GetType()

        if is_variant:
            logger >> "[TupleVariantSyntheticProvider] for " + str(valobj.GetName())
            self.size = self.type.GetNumberOfFields() - 1
        else:
            logger >> "[TupleSyntheticProvider] for " + str(valobj.GetName())
            self.size = self.type.GetNumberOfFields()

    def num_children(self):
        # type: () -> int
        return self.size

    def get_child_index(self, name):
        # type: (str) -> int
        if name.isdigit():
            return int(name)
        else:
            return -1

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        if self.is_variant:
            field = self.type.GetFieldAtIndex(index + 1)
        else:
            field = self.type.GetFieldAtIndex(index)
        element = self.valobj.GetChildMemberWithName(field.name)
        return self.valobj.CreateValueFromData(str(index), element.GetData(), element.GetType())

    def update(self):
        # type: () -> None
        pass

    def has_children(self):
        # type: () -> bool
        return True
