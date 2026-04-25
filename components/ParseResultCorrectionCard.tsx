import React from 'react';
import {
  Alert,
  Button,
  Card,
  Divider,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  IdeologyMatchInfo,
  ParseTaskCorrectionDraftInfo,
  PipelineKnowledgePointInfo,
  TeachingQuestionInfo,
} from '../services/api';

const { Text } = Typography;
const { TextArea } = Input;

interface ParseResultCorrectionCardProps {
  draft: ParseTaskCorrectionDraftInfo | null;
  loading: boolean;
  saving: boolean;
  reparsing: boolean;
  syncNotice?: string;
  onChange: (next: ParseTaskCorrectionDraftInfo) => void;
  onSave: () => void;
  onReparse: () => void;
}

const EMPTY_QUESTION: TeachingQuestionInfo = {
  stem: '',
  referenceAnswer: '',
  scoringPoints: [''],
};

const EMPTY_KNOWLEDGE_POINT: PipelineKnowledgePointInfo = {
  pointName: '',
  definition: '',
  chapter: '',
  importance: 'MEDIUM',
  evidenceSnippet: '',
};

const EMPTY_IDEOLOGY_MATCH: IdeologyMatchInfo = {
  knowledgePointName: '',
  ideologyElement: '',
  matchReason: '',
  confidence: 60,
};

export const ParseResultCorrectionCard: React.FC<ParseResultCorrectionCardProps> = ({
  draft,
  loading,
  saving,
  reparsing,
  syncNotice,
  onChange,
  onSave,
  onReparse,
}) => {
  const result = draft?.result;
  const documentStructure = result?.documentStructure ?? {
    title: '',
    documentType: 'UNKNOWN',
    overview: '',
    chapterOutline: [],
    teachingFocus: [],
  };
  const teachingArtifacts = result?.teachingArtifacts ?? {
    lectureNotes: '',
    cases: [],
    questions: [],
  };
  const knowledgePointOptions = (result?.knowledgePoints ?? [])
    .map((item) => item.pointName.trim())
    .filter(Boolean);

  const replaceResult = (nextResult: ParseTaskCorrectionDraftInfo['result']) => {
    if (!draft) {
      return;
    }
    onChange({
      ...draft,
      result: nextResult,
    });
  };

  const updateDocumentStructure = (
    field: 'title' | 'documentType' | 'overview' | 'chapterOutline' | 'teachingFocus',
    value: string | string[],
  ) => {
    if (!result) {
      return;
    }
    replaceResult({
      ...result,
      documentStructure: {
        ...documentStructure,
        [field]: value,
      },
    });
  };

  const updateStringList = (
    field: 'chapterOutline' | 'teachingFocus',
    index: number,
    value: string,
  ) => {
    const list = [...documentStructure[field]];
    list[index] = value;
    updateDocumentStructure(field, list);
  };

  const addStringListItem = (field: 'chapterOutline' | 'teachingFocus') => {
    updateDocumentStructure(field, [...documentStructure[field], '']);
  };

  const removeStringListItem = (field: 'chapterOutline' | 'teachingFocus', index: number) => {
    updateDocumentStructure(
      field,
      documentStructure[field].filter((_, currentIndex) => currentIndex !== index),
    );
  };

  const updateKnowledgePoint = (
    index: number,
    field: keyof PipelineKnowledgePointInfo,
    value: string,
  ) => {
    if (!result) {
      return;
    }
    const knowledgePoints = [...result.knowledgePoints];
    const current = knowledgePoints[index] ?? EMPTY_KNOWLEDGE_POINT;
    knowledgePoints[index] = {
      ...current,
      [field]: value,
    };
    replaceResult({
      ...result,
      knowledgePoints,
    });
  };

  const addKnowledgePoint = () => {
    if (!result) {
      return;
    }
    replaceResult({
      ...result,
      knowledgePoints: [...result.knowledgePoints, { ...EMPTY_KNOWLEDGE_POINT }],
    });
  };

  const removeKnowledgePoint = (index: number) => {
    if (!result) {
      return;
    }
    const knowledgePoints = result.knowledgePoints.filter((_, currentIndex) => currentIndex !== index);
    const removedPointName = result.knowledgePoints[index]?.pointName?.trim() || '';
    const ideologyMatches = removedPointName
      ? result.ideologyMatches.filter((item) => item.knowledgePointName.trim() !== removedPointName)
      : [...result.ideologyMatches];
    replaceResult({
      ...result,
      knowledgePoints,
      ideologyMatches,
    });
  };

  const updateIdeologyMatch = (
    index: number,
    field: keyof IdeologyMatchInfo,
    value: string | number,
  ) => {
    if (!result) {
      return;
    }
    const ideologyMatches = [...result.ideologyMatches];
    const current = ideologyMatches[index] ?? EMPTY_IDEOLOGY_MATCH;
    ideologyMatches[index] = {
      ...current,
      [field]: value,
    };
    replaceResult({
      ...result,
      ideologyMatches,
    });
  };

  const addIdeologyMatch = () => {
    if (!result) {
      return;
    }
    replaceResult({
      ...result,
      ideologyMatches: [
        ...result.ideologyMatches,
        {
          ...EMPTY_IDEOLOGY_MATCH,
          knowledgePointName: knowledgePointOptions[0] ?? '',
        },
      ],
    });
  };

  const removeIdeologyMatch = (index: number) => {
    if (!result) {
      return;
    }
    replaceResult({
      ...result,
      ideologyMatches: result.ideologyMatches.filter((_, currentIndex) => currentIndex !== index),
    });
  };

  const updateTeachingArtifacts = (
    field: 'lectureNotes' | 'cases' | 'questions',
    value: string | string[] | TeachingQuestionInfo[],
  ) => {
    if (!result) {
      return;
    }
    replaceResult({
      ...result,
      teachingArtifacts: {
        ...teachingArtifacts,
        [field]: value,
      },
    });
  };

  const updateCase = (index: number, value: string) => {
    const cases = [...teachingArtifacts.cases];
    cases[index] = value;
    updateTeachingArtifacts('cases', cases);
  };

  const addCase = () => {
    updateTeachingArtifacts('cases', [...teachingArtifacts.cases, '']);
  };

  const removeCase = (index: number) => {
    updateTeachingArtifacts(
      'cases',
      teachingArtifacts.cases.filter((_, currentIndex) => currentIndex !== index),
    );
  };

  const updateQuestion = (
    index: number,
    field: keyof TeachingQuestionInfo,
    value: string | string[],
  ) => {
    const questions = [...teachingArtifacts.questions];
    const current = questions[index] ?? EMPTY_QUESTION;
    questions[index] = {
      ...current,
      [field]: value,
    };
    updateTeachingArtifacts('questions', questions);
  };

  const addQuestion = () => {
    updateTeachingArtifacts('questions', [...teachingArtifacts.questions, { ...EMPTY_QUESTION }]);
  };

  const removeQuestion = (index: number) => {
    updateTeachingArtifacts(
      'questions',
      teachingArtifacts.questions.filter((_, currentIndex) => currentIndex !== index),
    );
  };

  const updateScoringPoint = (questionIndex: number, pointIndex: number, value: string) => {
    const question = teachingArtifacts.questions[questionIndex] ?? EMPTY_QUESTION;
    const scoringPoints = [...(question.scoringPoints || [])];
    scoringPoints[pointIndex] = value;
    updateQuestion(questionIndex, 'scoringPoints', scoringPoints);
  };

  const addScoringPoint = (questionIndex: number) => {
    const question = teachingArtifacts.questions[questionIndex] ?? EMPTY_QUESTION;
    updateQuestion(questionIndex, 'scoringPoints', [...(question.scoringPoints || []), '']);
  };

  const removeScoringPoint = (questionIndex: number, pointIndex: number) => {
    const question = teachingArtifacts.questions[questionIndex] ?? EMPTY_QUESTION;
    updateQuestion(
      questionIndex,
      'scoringPoints',
      (question.scoringPoints || []).filter((_, currentIndex) => currentIndex !== pointIndex),
    );
  };

  return (
    <Card
      title="Parse Result Correction"
      bordered={false}
      style={{ borderRadius: 12 }}
      extra={(
        <Space>
          <Button onClick={onSave} loading={saving} disabled={!draft || loading || reparsing}>
            Save Correction Draft
          </Button>
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={onReparse}
            loading={reparsing}
            disabled={!draft || loading || saving}
          >
            Reparse
          </Button>
        </Space>
      )}
    >
      {loading && (
        <div style={{ padding: 12 }}>
          <Spin />
        </div>
      )}

      {!loading && !draft && (
        <Text type="secondary">No correction draft is available for this task.</Text>
      )}

      {!loading && draft && result && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Tag color={draft.source === 'CORRECTION' ? 'green' : 'blue'}>
              {draft.source === 'CORRECTION' ? 'Using Correction' : 'Using Pipeline Baseline'}
            </Tag>
            {draft.savedAt && <Tag color="default">Saved {new Date(draft.savedAt).toLocaleString()}</Tag>}
            {draft.sourceCompletedAt && (
              <Tag color="gold">Baseline {new Date(draft.sourceCompletedAt).toLocaleString()}</Tag>
            )}
          </Space>

          {draft.stale && (
            <Alert
              type="warning"
              showIcon
              message="An older correction snapshot exists, but it does not match the latest completed parse result."
            />
          )}

          {syncNotice && (
            <Alert
              type="info"
              showIcon
              message={syncNotice}
            />
          )}

          <Card size="small" style={{ borderRadius: 8 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Text strong>Document Structure</Text>
              <div>
                <Text strong>Title</Text>
                <Input
                  value={documentStructure.title}
                  onChange={(event) => updateDocumentStructure('title', event.target.value)}
                  maxLength={120}
                />
              </div>
              <div>
                <Text strong>Document Type</Text>
                <Select
                  value={documentStructure.documentType}
                  onChange={(value) => updateDocumentStructure('documentType', value)}
                  style={{ width: '100%' }}
                  options={[
                    { label: 'Textbook', value: 'TEXTBOOK' },
                    { label: 'Outline', value: 'OUTLINE' },
                    { label: 'Paper', value: 'PAPER' },
                    { label: 'Unknown', value: 'UNKNOWN' },
                  ]}
                />
              </div>
              <div>
                <Text strong>Overview</Text>
                <TextArea
                  value={documentStructure.overview}
                  onChange={(event) => updateDocumentStructure('overview', event.target.value)}
                  autoSize={{ minRows: 3, maxRows: 6 }}
                  maxLength={1200}
                />
              </div>

              <Divider style={{ margin: '8px 0' }} />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Chapter Outline</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={() => addStringListItem('chapterOutline')}>
                  Add
                </Button>
              </Space>
              {(documentStructure.chapterOutline || []).map((item, index) => (
                <div key={`chapter-${index}`} style={{ display: 'flex', gap: 8, width: '100%' }}>
                  <Input
                    value={item}
                    onChange={(event) => updateStringList('chapterOutline', index, event.target.value)}
                    maxLength={200}
                    style={{ flex: 1, minWidth: 0 }}
                  />
                  <Button danger style={{ flexShrink: 0 }} onClick={() => removeStringListItem('chapterOutline', index)}>
                    Remove
                  </Button>
                </div>
              ))}

              <Divider style={{ margin: '8px 0' }} />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Teaching Focus</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={() => addStringListItem('teachingFocus')}>
                  Add
                </Button>
              </Space>
              {(documentStructure.teachingFocus || []).map((item, index) => (
                <div key={`focus-${index}`} style={{ display: 'flex', gap: 8, width: '100%' }}>
                  <Input
                    value={item}
                    onChange={(event) => updateStringList('teachingFocus', index, event.target.value)}
                    maxLength={200}
                    style={{ flex: 1, minWidth: 0 }}
                  />
                  <Button danger style={{ flexShrink: 0 }} onClick={() => removeStringListItem('teachingFocus', index)}>
                    Remove
                  </Button>
                </div>
              ))}
            </Space>
          </Card>

          <Card size="small" style={{ borderRadius: 8 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Knowledge Points</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={addKnowledgePoint}>
                  Add Point
                </Button>
              </Space>
              {result.knowledgePoints.map((point, index) => (
                <Card key={`point-${index}`} size="small" style={{ borderRadius: 8 }}>
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    <Input
                      placeholder="Point name"
                      value={point.pointName}
                      onChange={(event) => updateKnowledgePoint(index, 'pointName', event.target.value)}
                      maxLength={120}
                    />
                    <Input
                      placeholder="Chapter"
                      value={point.chapter}
                      onChange={(event) => updateKnowledgePoint(index, 'chapter', event.target.value)}
                      maxLength={120}
                    />
                    <Select
                      value={point.importance}
                      onChange={(value) => updateKnowledgePoint(index, 'importance', value)}
                      style={{ width: '100%' }}
                      options={[
                        { label: 'High', value: 'HIGH' },
                        { label: 'Medium', value: 'MEDIUM' },
                        { label: 'Low', value: 'LOW' },
                      ]}
                    />
                    <TextArea
                      placeholder="Definition"
                      value={point.definition}
                      onChange={(event) => updateKnowledgePoint(index, 'definition', event.target.value)}
                      autoSize={{ minRows: 2, maxRows: 5 }}
                      maxLength={500}
                    />
                    <TextArea
                      placeholder="Evidence snippet"
                      value={point.evidenceSnippet}
                      onChange={(event) => updateKnowledgePoint(index, 'evidenceSnippet', event.target.value)}
                      autoSize={{ minRows: 2, maxRows: 4 }}
                      maxLength={300}
                    />
                    <Button danger onClick={() => removeKnowledgePoint(index)}>
                      Remove Point
                    </Button>
                  </Space>
                </Card>
              ))}
              {result.knowledgePoints.length === 0 && (
                <Text type="secondary">No knowledge points yet.</Text>
              )}
            </Space>
          </Card>

          <Card size="small" style={{ borderRadius: 8 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Ideology Matches</Text>
                <Button
                  size="small"
                  icon={<PlusOutlined />}
                  onClick={addIdeologyMatch}
                  disabled={knowledgePointOptions.length === 0}
                >
                  Add Match
                </Button>
              </Space>
              {result.ideologyMatches.map((match, index) => (
                <Card key={`match-${index}`} size="small" style={{ borderRadius: 8 }}>
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    <Select
                      value={match.knowledgePointName}
                      onChange={(value) => updateIdeologyMatch(index, 'knowledgePointName', value)}
                      style={{ width: '100%' }}
                      options={knowledgePointOptions.map((item) => ({ label: item, value: item }))}
                    />
                    <Input
                      placeholder="Ideology element"
                      value={match.ideologyElement}
                      onChange={(event) => updateIdeologyMatch(index, 'ideologyElement', event.target.value)}
                      maxLength={120}
                    />
                    <Input
                      placeholder="Confidence (0-100)"
                      type="number"
                      value={match.confidence}
                      onChange={(event) => updateIdeologyMatch(index, 'confidence', Number(event.target.value))}
                    />
                    <TextArea
                      placeholder="Match reason"
                      value={match.matchReason}
                      onChange={(event) => updateIdeologyMatch(index, 'matchReason', event.target.value)}
                      autoSize={{ minRows: 2, maxRows: 5 }}
                      maxLength={500}
                    />
                    <Button danger onClick={() => removeIdeologyMatch(index)}>
                      Remove Match
                    </Button>
                  </Space>
                </Card>
              ))}
              {result.ideologyMatches.length === 0 && (
                <Text type="secondary">No ideology matches yet.</Text>
              )}
            </Space>
          </Card>

          <Card size="small" style={{ borderRadius: 8 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Text strong>Teaching Artifacts</Text>
              <div>
                <Text strong>Lecture Notes</Text>
                <TextArea
                  value={teachingArtifacts.lectureNotes}
                  onChange={(event) => updateTeachingArtifacts('lectureNotes', event.target.value)}
                  autoSize={{ minRows: 4, maxRows: 10 }}
                  maxLength={5000}
                />
              </div>

              <Divider style={{ margin: '8px 0' }} />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Cases</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={addCase}>
                  Add Case
                </Button>
              </Space>
              {teachingArtifacts.cases.map((item, index) => (
                <div key={`case-${index}`} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, width: '100%' }}>
                  <TextArea
                    value={item}
                    onChange={(event) => updateCase(index, event.target.value)}
                    autoSize={{ minRows: 2, maxRows: 4 }}
                    maxLength={2000}
                    style={{ flex: 1, minWidth: 0 }}
                  />
                  <Button danger style={{ flexShrink: 0 }} onClick={() => removeCase(index)}>
                    Remove
                  </Button>
                </div>
              ))}

              <Divider style={{ margin: '8px 0' }} />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Questions</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={addQuestion}>
                  Add Question
                </Button>
              </Space>
              {teachingArtifacts.questions.map((question, index) => (
                <Card key={`question-${index}`} size="small" style={{ borderRadius: 8 }}>
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    <Input
                      placeholder="Question stem"
                      value={question.stem}
                      onChange={(event) => updateQuestion(index, 'stem', event.target.value)}
                      maxLength={2000}
                    />
                    <TextArea
                      placeholder="Reference answer"
                      value={question.referenceAnswer}
                      onChange={(event) => updateQuestion(index, 'referenceAnswer', event.target.value)}
                      autoSize={{ minRows: 2, maxRows: 5 }}
                      maxLength={3000}
                    />
                    <Text type="secondary">Scoring Points</Text>
                    {(question.scoringPoints || []).map((point, pointIndex) => (
                      <div key={`score-${index}-${pointIndex}`} style={{ display: 'flex', gap: 8, width: '100%' }}>
                        <Input
                          value={point}
                          onChange={(event) => updateScoringPoint(index, pointIndex, event.target.value)}
                          maxLength={2000}
                          style={{ flex: 1, minWidth: 0 }}
                        />
                        <Button danger style={{ flexShrink: 0 }} onClick={() => removeScoringPoint(index, pointIndex)}>
                          Remove
                        </Button>
                      </div>
                    ))}
                    <Button size="small" onClick={() => addScoringPoint(index)}>
                      Add Scoring Point
                    </Button>
                    <Button danger onClick={() => removeQuestion(index)}>
                      Remove Question
                    </Button>
                  </Space>
                </Card>
              ))}
            </Space>
          </Card>
        </Space>
      )}
    </Card>
  );
};
