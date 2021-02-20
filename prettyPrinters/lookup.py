import lldb
import re

from providers import *


class RustType:
    OTHER = "Other"
    STRUCT = "Struct"
    TUPLE = "Tuple"
    CSTYLE_VARIANT = "CStyleVariant"
    TUPLE_VARIANT = "TupleVariant"
    STRUCT_VARIANT = "StructVariant"
    EMPTY = "Empty"
    SINGLETON_ENUM = "SingletonEnum"
    REGULAR_ENUM = "RegularEnum"
    COMPRESSED_ENUM = "CompressedEnum"
    REGULAR_UNION = "RegularUnion"

    STD_VEC = "StdVec"
    STD_STRING = "StdString"
    STD_STR = "StdStr"


STD_VEC_REGEX = re.compile(r"^(alloc::([a-zA-Z]+::)+)Vec<.+>$")
STD_STRING_REGEX = re.compile(r"^(alloc::([a-zA-Z]+::)+)String$")
STD_STR_REGEX = re.compile(r"^&str$")

TUPLE_ITEM_REGEX = re.compile(r"__\d+$")

ENCODED_ENUM_PREFIX = "RUST$ENCODED$ENUM$"
ENUM_DISR_FIELD_NAME = "RUST$ENUM$DISR"


def is_tuple_fields(fields):
    # type: (list) -> bool
    return all(re.match(TUPLE_ITEM_REGEX, str(field.name)) for field in fields)


def classify_rust_type(type):
    # type: (SBType) -> str
    type_class = type.GetTypeClass()
    fields = type.fields

    if type_class == lldb.eTypeClassStruct:
        if len(fields) == 0:
            return RustType.EMPTY

        name = type.GetName()
        if re.match(STD_VEC_REGEX, name):
            return RustType.STD_VEC
        if re.match(STD_STRING_REGEX, name):
            return RustType.STD_STRING
        if re.match(STD_STR_REGEX, name):
            return RustType.STD_STR

        if fields[0].name == ENUM_DISR_FIELD_NAME:
            if len(fields) == 1:
                return RustType.CSTYLE_VARIANT
            if is_tuple_fields(fields[1:]):
                return RustType.TUPLE_VARIANT
            else:
                return RustType.STRUCT_VARIANT

        if is_tuple_fields(fields):
            return RustType.TUPLE

        else:
            return RustType.STRUCT

    if type_class == lldb.eTypeClassUnion:
        if len(fields) == 0:
            return RustType.EMPTY

        first_variant_name = fields[0].name
        if first_variant_name is None:
            if len(fields) == 1:
                return RustType.SINGLETON_ENUM
            else:
                return RustType.REGULAR_ENUM
        elif first_variant_name.startswith(ENCODED_ENUM_PREFIX):
            assert len(fields) == 1
            return RustType.COMPRESSED_ENUM
        else:
            return RustType.REGULAR_UNION

    return RustType.OTHER


def summary_lookup(valobj, dict):
    # type: (SBValue, dict) -> str
    """Returns the summary provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_VEC:
        return SizeSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_STRING:
        return StdStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_STR:
        return StdStrSummaryProvider(valobj, dict)

    return ""


def synthetic_lookup(valobj, dict):
    # type: (SBValue, dict) -> object
    """Returns the synthetic provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_VEC:
        return StdVecSyntheticProvider(valobj, dict)
    if rust_type == RustType.STRUCT:
        return StructSyntheticProvider(valobj, dict)
    if rust_type == RustType.STRUCT_VARIANT:
        return StructSyntheticProvider(valobj, dict, is_variant=True)
    if rust_type == RustType.TUPLE:
        return TupleSyntheticProvider(valobj, dict)
    if rust_type == RustType.TUPLE_VARIANT:
        return TupleSyntheticProvider(valobj, dict, is_variant=True)
    if rust_type == RustType.EMPTY:
        return EmptySyntheticProvider(valobj, dict)

    if rust_type == RustType.REGULAR_ENUM:
        discriminant = valobj.GetChildAtIndex(0).GetChildAtIndex(0).GetValueAsUnsigned()
        return synthetic_lookup(valobj.GetChildAtIndex(discriminant), dict)
    if rust_type == RustType.SINGLETON_ENUM:
        return synthetic_lookup(valobj.GetChildAtIndex(0), dict)

    return DefaultSynthteticProvider(valobj, dict)
