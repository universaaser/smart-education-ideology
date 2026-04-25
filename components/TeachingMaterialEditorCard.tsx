import React from 'react';
import { Alert, Button, Card, Divider, Input, InputNumber, Select, Space, Spin, Tabs, Typography } from 'antd';
import { DownloadOutlined, FileTextOutlined, RobotOutlined } from '@ant-design/icons';
import {
  MaterialVersionItemInfo,
  SelectionExplainHistoryInfo,
  SelectionExplainResponse,
  TeachingMaterialDraftInfo,
  TeachingQuestionInfo,
} from '../services/api';
import { SelectionExplainPanel } from './SelectionExplainPanel';
import { TeachingMaterialPreview } from './TeachingMaterialPreview';

const { Text } = Typography;
const { TextArea } = Input;

interface TeachingMaterialEditorCardProps {
  editorLoading: boolean;
  editorDraft: TeachingMaterialDraftInfo | null;
  savingDraft: boolean;
  publishingVersion: boolean;
  exportingMarkdown: boolean;
  versionsLoading: boolean;
  materialVersions: MaterialVersionItemInfo[];
  rollbackTip: string;
  selectionExplainLoading: boolean;
  selectionExplainResult: SelectionExplainResponse | null;
  selectionExplainHistory: SelectionExplainHistoryInfo[];
  selectionHistoryLoading: boolean;
  onSaveDraft: () => void;
  onPublishVersion: () => void;
  onExportMarkdown: () => void;
  onRefreshVersions: () => void;
  onViewVersion: (materialId: number) => void;
  onRollbackVersion: (materialId: number, versionNo: number) => void;
  onChangeTitle: (value: string) => void;
  onChangeLectureNotes: (value: string) => void;
  onLectureSelection: (event: React.SyntheticEvent<HTMLTextAreaElement>) => void;
  onExplainSelection: () => void;
  onRefreshSelectionHistory: () => void;
  onCopySelectionExplanation: (answer: string) => void;
  onAppendSelectionExplanation: (answer: string) => void;
  onAddCase: () => void;
  onUpdateCase: (index: number, value: string) => void;
  onRemoveCase: (index: number) => void;
  onAddQuestion: () => void;
  onUpdateQuestion: (index: number, field: keyof TeachingQuestionInfo, value: string | string[] | number | null) => void;
  onRemoveQuestion: (index: number) => void;
  onUpdateQuestionOption: (questionIndex: number, optionIndex: number, value: string) => void;
  onAddQuestionOption: (questionIndex: number) => void;
  onRemoveQuestionOption: (questionIndex: number, optionIndex: number) => void;
  onUpdateScoringPoint: (questionIndex: number, pointIndex: number, value: string) => void;
  onAddScoringPoint: (questionIndex: number) => void;
  onRemoveScoringPoint: (questionIndex: number, pointIndex: number) => void;
}

export const TeachingMaterialEditorCard: React.FC<TeachingMaterialEditorCardProps> = ({
  editorLoading,
  editorDraft,
  savingDraft,
  publishingVersion,
  exportingMarkdown,
  versionsLoading,
  materialVersions,
  rollbackTip,
  selectionExplainLoading,
  selectionExplainResult,
  selectionExplainHistory,
  selectionHistoryLoading,
  onSaveDraft,
  onPublishVersion,
  onExportMarkdown,
  onRefreshVersions,
  onViewVersion,
  onRollbackVersion,
  onChangeTitle,
  onChangeLectureNotes,
  onLectureSelection,
  onExplainSelection,
  onRefreshSelectionHistory,
  onCopySelectionExplanation,
  onAppendSelectionExplanation,
  onAddCase,
  onUpdateCase,
  onRemoveCase,
  onAddQuestion,
  onUpdateQuestion,
  onRemoveQuestion,
  onUpdateQuestionOption,
  onAddQuestionOption,
  onRemoveQuestionOption,
  onUpdateScoringPoint,
  onAddScoringPoint,
  onRemoveScoringPoint,
}) => {
  return (
    <Card
      title={<Space><FileTextOutlined style={{ color: '#1677ff' }} /> Teaching Editor</Space>}
      bordered={false}
      style={{ borderRadius: 12 }}
      extra={(
        <Space>
          <Button onClick={onSaveDraft} loading={savingDraft} disabled={!editorDraft || editorLoading}>
            Save Draft
          </Button>
          <Button
            type="primary"
            onClick={onPublishVersion}
            loading={publishingVersion}
            disabled={!editorDraft || editorLoading}
          >
            Save Version
          </Button>
          <Button
            icon={<DownloadOutlined />}
            onClick={onExportMarkdown}
            loading={exportingMarkdown}
            disabled={!editorDraft || editorLoading}
          >
            Export Markdown
          </Button>
        </Space>
      )}
    >
      {editorLoading && (
        <div style={{ padding: 12 }}>
          <Spin />
        </div>
      )}

      {!editorLoading && editorDraft && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text strong>Version History</Text>
                <Button
                  size="small"
                  onClick={onRefreshVersions}
                  loading={versionsLoading}
                >
                  Refresh
                </Button>
              </Space>
              {materialVersions.length === 0 && (
                <Text type="secondary">No saved versions yet.</Text>
              )}
              {materialVersions.map((version) => (
                <Space
                  key={`version-${version.materialId}`}
                  style={{ width: '100%', justifyContent: 'space-between' }}
                >
                  <Text type={version.materialId === editorDraft.materialId ? 'success' : undefined}>
                    v{version.versionNo} | {version.status} {version.isLatest === 1 ? '(latest)' : ''}
                  </Text>
                  <Space>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {version.updatedAt || '-'}
                    </Text>
                    <Button size="small" onClick={() => onViewVersion(version.materialId)}>
                      View
                    </Button>
                    <Button
                      size="small"
                      disabled={version.materialId === editorDraft.materialId && editorDraft.status === 'DRAFT'}
                      onClick={() => onRollbackVersion(version.materialId, version.versionNo)}
                    >
                      Rollback to Draft
                    </Button>
                  </Space>
                </Space>
              ))}
            </Space>
          </Card>

          <Alert
            type="info"
            showIcon
            message={`Version: ${editorDraft.versionNo || 0} | Status: ${editorDraft.status || 'DRAFT'} | Updated: ${editorDraft.updatedAt || '-'}`}
          />

          {rollbackTip && (
            <Alert type="warning" showIcon message={rollbackTip} />
          )}

          <Tabs
            items={[
              {
                key: 'edit',
                label: 'Edit',
                children: (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <div>
                      <Text strong>Title</Text>
                      <Input
                        value={editorDraft.title}
                        onChange={(event) => onChangeTitle(event.target.value)}
                        maxLength={300}
                      />
                    </div>

          <div>
            <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 6 }}>
              <Text strong>Lecture Notes</Text>
              <Space>
                <Button
                  size="small"
                  icon={<RobotOutlined />}
                  onClick={onExplainSelection}
                  loading={selectionExplainLoading}
                  disabled={editorLoading}
                >
                  Explain Selection
                </Button>
                <Button
                  size="small"
                  onClick={onRefreshSelectionHistory}
                  loading={selectionHistoryLoading}
                >
                  Refresh History
                </Button>
              </Space>
            </Space>
            <TextArea
              value={editorDraft.lectureNotes}
              onChange={(event) => onChangeLectureNotes(event.target.value)}
              onSelect={onLectureSelection}
              onKeyUp={onLectureSelection}
              onMouseUp={onLectureSelection}
              autoSize={{ minRows: 6, maxRows: 14 }}
              maxLength={10000}
            />
          </div>

          <SelectionExplainPanel
            selectionExplainResult={selectionExplainResult}
            selectionExplainHistory={selectionExplainHistory}
            selectionHistoryLoading={selectionHistoryLoading}
            onCopySelectionExplanation={onCopySelectionExplanation}
            onAppendSelectionExplanation={onAppendSelectionExplanation}
          />

          <Divider style={{ margin: '8px 0' }} />
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong>Cases</Text>
            <Button size="small" onClick={onAddCase}>
              Add Case
            </Button>
          </Space>
          <Space direction="vertical" style={{ width: '100%' }}>
            {(editorDraft.cases || []).map((item, index) => (
              <div key={`case-${index}`} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, width: '100%' }}>
                <TextArea
                  value={item}
                  onChange={(event) => onUpdateCase(index, event.target.value)}
                  autoSize={{ minRows: 2, maxRows: 6 }}
                  maxLength={2000}
                  style={{ flex: 1, minWidth: 0 }}
                />
                <Button danger style={{ flexShrink: 0 }} onClick={() => onRemoveCase(index)}>
                  Remove
                </Button>
              </div>
            ))}
          </Space>

          <Divider style={{ margin: '8px 0' }} />
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong>Questions</Text>
            <Button size="small" onClick={onAddQuestion}>
              Add Question
            </Button>
          </Space>
          <Space direction="vertical" style={{ width: '100%' }}>
            {(editorDraft.questions || []).map((question, index) => (
              <Card key={`question-${index}`} size="small" style={{ borderRadius: 8 }}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Space wrap>
                    <Select
                      style={{ width: 180 }}
                      value={question.questionType || 'SHORT_ANSWER'}
                      onChange={(value) => onUpdateQuestion(index, 'questionType', value)}
                      options={[
                        { value: 'SINGLE_CHOICE', label: 'Single Choice' },
                        { value: 'MULTIPLE_CHOICE', label: 'Multiple Choice' },
                        { value: 'SHORT_ANSWER', label: 'Short Answer' },
                        { value: 'CASE_ANALYSIS', label: 'Case Analysis' },
                      ]}
                    />
                    <Select
                      style={{ width: 140 }}
                      value={question.difficulty || 'MEDIUM'}
                      onChange={(value) => onUpdateQuestion(index, 'difficulty', value)}
                      options={[
                        { value: 'EASY', label: 'Easy' },
                        { value: 'MEDIUM', label: 'Medium' },
                        { value: 'HARD', label: 'Hard' },
                      ]}
                    />
                    <InputNumber
                      placeholder="Knowledge point ID"
                      min={1}
                      value={question.knowledgePointId ?? null}
                      onChange={(value) => onUpdateQuestion(index, 'knowledgePointId', value)}
                    />
                  </Space>
                  <Input
                    placeholder="Question stem"
                    value={question.stem}
                    onChange={(event) => onUpdateQuestion(index, 'stem', event.target.value)}
                    maxLength={2000}
                  />
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Text type="secondary">Options</Text>
                    {(question.options || []).map((option, optionIndex) => (
                      <div key={`question-${index}-option-${optionIndex}`} style={{ display: 'flex', gap: 8, width: '100%' }}>
                        <Input
                          value={option}
                          onChange={(event) => onUpdateQuestionOption(index, optionIndex, event.target.value)}
                          maxLength={1000}
                          style={{ flex: 1, minWidth: 0 }}
                        />
                        <Button danger style={{ flexShrink: 0 }} onClick={() => onRemoveQuestionOption(index, optionIndex)}>
                          Remove
                        </Button>
                      </div>
                    ))}
                    <Button size="small" onClick={() => onAddQuestionOption(index)}>
                      Add Option
                    </Button>
                  </Space>
                  <TextArea
                    placeholder="Reference answer"
                    value={question.referenceAnswer}
                    onChange={(event) => onUpdateQuestion(index, 'referenceAnswer', event.target.value)}
                    autoSize={{ minRows: 2, maxRows: 6 }}
                    maxLength={3000}
                  />
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Text type="secondary">Scoring Points</Text>
                    {(question.scoringPoints || []).map((point, pointIndex) => (
                      <div key={`question-${index}-point-${pointIndex}`} style={{ display: 'flex', gap: 8, width: '100%' }}>
                        <Input
                          value={point}
                          onChange={(event) => onUpdateScoringPoint(index, pointIndex, event.target.value)}
                          maxLength={2000}
                          style={{ flex: 1, minWidth: 0 }}
                        />
                        <Button danger style={{ flexShrink: 0 }} onClick={() => onRemoveScoringPoint(index, pointIndex)}>
                          Remove
                        </Button>
                      </div>
                    ))}
                    <Button size="small" onClick={() => onAddScoringPoint(index)}>
                      Add Scoring Point
                    </Button>
                  </Space>
                  <Button danger onClick={() => onRemoveQuestion(index)}>
                    Remove Question
                  </Button>
                </Space>
              </Card>
            ))}
          </Space>
                  </Space>
                ),
              },
              {
                key: 'preview',
                label: 'Preview',
                children: <TeachingMaterialPreview material={editorDraft} />,
              },
            ]}
          />
        </Space>
      )}
    </Card>
  );
};
