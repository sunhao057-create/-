# -*- coding: utf-8 -*-
import unittest

from map_qingflow import approved, coerce_member, coerce_ref, extra_from_fields, map_subtable


class MapQingflowTest(unittest.TestCase):
    def test_approved_status(self):
        self.assertTrue(approved('已通过'))
        self.assertTrue(approved(['已通过']))
        self.assertTrue(approved('["已通过"]'))
        self.assertFalse(approved('进行中'))
        self.assertFalse(approved(None))

    def test_member_first_match(self):
        staff = [{"id": 3, "displayName": "张嘉"}, {"id": 8, "displayName": "魏永刚"}]
        self.assertEqual(coerce_member(["张嘉", "魏永刚"], staff), 3)
        self.assertIsNone(coerce_member(["路人"], staff))

    def test_extra_and_subtable(self):
        kind = {
            "fields": [
                {"key": "field_1", "name": "客户名称", "type": "text"},
                {"key": "field_2", "name": "负责工程师", "type": "member"},
                {
                    "key": "field_3",
                    "name": "仪器设备信息",
                    "type": "subtable",
                    "subfields": [
                        {"key": "field_31", "name": "仪器名称", "type": "text"},
                        {"key": "field_32", "name": "照片", "type": "attachment"},
                    ],
                },
            ]
        }
        fields = [
            {"field_id": 1, "value": "空军医院"},
            {"field_id": 2, "value": ["张嘉"]},
            {
                "field_id": 3,
                "value": [{"仪器名称": "熔蜡锅", "照片": [{"name": "a.jpg"}], "__row_id__": 1}],
            },
        ]
        staff = [{"id": 3, "displayName": "张嘉"}]
        extra = extra_from_fields(kind, fields, staff)
        self.assertEqual(extra["field_1"], "空军医院")
        self.assertEqual(extra["field_2"], 3)
        self.assertEqual(extra["field_3"], [{"field_31": "熔蜡锅"}])
        rows = map_subtable(kind["fields"][2], {"rows": [{"仪器名称": "A"}]}, staff)
        self.assertEqual(rows, [{"field_31": "A"}])

    def test_relation_not_json_dumped(self):
        self.assertIsNone(coerce_ref({"apply_id": "575663821", "label": None}))
        self.assertEqual(coerce_ref({"apply_id": "1", "label": "李莉"}), "李莉")
        kind = {
            "fields": [
                {
                    "key": "field_3",
                    "name": "客户联系人信息",
                    "type": "subtable",
                    "subfields": [
                        {"key": "field_c", "name": "联系人", "type": "text"},
                        {"key": "field_n", "name": "姓名", "type": "text"},
                    ],
                }
            ]
        }
        rows = map_subtable(
            kind["fields"][0],
            {"rows": [{"联系人": {"apply_id": "575663821", "label": None}, "姓名": "王园"}]},
            [],
        )
        self.assertEqual(rows, [{"field_c": "王园", "field_n": "王园"}])


if __name__ == "__main__":
    unittest.main()
