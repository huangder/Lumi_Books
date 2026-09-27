import copy
import json
import unittest
from pathlib import Path
from build import Policy, normalize


class FilterTests(unittest.TestCase):
    def setUp(self):
        self.obj = {'version':1,'charMap':{'臺':'台','灣':'湾'},'rules':[
            {'id':'test','category':'abuse','text':'badword','mode':'word','fields':['headword','definition','example','related'],'exceptions':[]}]}
        self.entry = {'id':'entry','headword':'hello','aliases':[], 'senses':[
            {'id':'1','definition':'greeting','examples':[{'text':'hello','translation':'BADWORD'}], 'related':['good','badword']},
            {'id':'2','definition':'BADWORD in definition','examples':[]}]}

    def test_partial_removes_sense_pair_and_related(self):
        result, filtered = Policy(self.obj).apply('book', self.entry)
        self.assertTrue(filtered)
        self.assertEqual(len(result['senses']), 1)
        self.assertEqual(result['senses'][0]['examples'], [])
        self.assertEqual(result['senses'][0]['related'], ['good'])
        self.assertEqual(len(self.entry['senses']), 2)

    def test_alias_whole_word_and_normalization(self):
        self.entry['aliases']=['ＢＡＤＷＯＲＤ']
        self.assertIsNone(Policy(self.obj).apply('book',self.entry)[0])
        self.assertFalse(Policy(self.obj).matches('badwords','headword','book','entry'))
        self.assertEqual(normalize(' HELLO\n world '), 'hello world')

    def test_exception_is_sense_scoped(self):
        self.obj['rules'][0]['exceptions']=[{'dictionaryId':'book','entryId':'entry','senseId':'2'}]
        result,_=Policy(self.obj).apply('book',self.entry)
        self.assertEqual(len(result['senses']),2)
        self.assertTrue(Policy(self.obj).matches('badword','definition','other','entry','2'))

    def test_repository_policy_protects_normal_places_and_anatomy(self):
        root=Path(__file__).resolve().parents[2]
        p=Policy(json.loads((root/'app/src/main/assets/dictionaries/filter-policy.json').read_text('utf-8')))
        for term in ['台湾','臺灣','北京','西藏','Taiwan','China','vagina','penis','cancer','masturbation']:
            self.assertFalse(p.matches(term,'headword','book','entry'), term)


if __name__=='__main__': unittest.main()
