import React, { useCallback, useEffect, useState } from 'react';
import { View } from '../types';
import {
  dashboardApi,
  knowledgeApi,
  pathApi,
  studentActivityApi,
  studentQuizApi,
  alertApi,
  courseApi,
  LearningPathResult,
  ActivityInfo,
  CourseInfo,
  CourseTeachingMaterialGroupInfo,
  StudentLearningReportInfo,
  StudentRecentActivityInfo,
  StudentAlertRecordInfo,
  StudentQuizQuestionInfo,
  StudentQuizSubmitResultInfo,
} from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import {
  Typography, Card, Row, Col, Statistic, Button,
  List, Avatar, Spin, Space, Empty, Tooltip, Select, Radio, Alert
} from 'antd';
import {
  RobotOutlined, ShareAltOutlined, BookOutlined, BlockOutlined,
  NodeIndexOutlined, NotificationOutlined, SyncOutlined, ArrowRightOutlined,
  CloudUploadOutlined, InfoCircleOutlined, CompassOutlined
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;

interface StudentHomeProps {
  onChangeView: (view: View, options?: { highlightNodeIds?: number[] }) => void;
}

export const StudentHome: React.FC<StudentHomeProps> = ({ onChangeView }) => {
  const { currentUser } = useAuth();
  const [totalNodes, setTotalNodes] = useState<number>(0);
  const [activities, setActivities] = useState<ActivityInfo[]>([]);
  const [learningReport, setLearningReport] = useState<StudentLearningReportInfo | null>(null);
  const [recentLearningActivities, setRecentLearningActivities] = useState<StudentRecentActivityInfo[]>([]);
  const [feedbackAlerts, setFeedbackAlerts] = useState<StudentAlertRecordInfo[]>([]);
  const [studentCourses, setStudentCourses] = useState<CourseInfo[]>([]);
  const [courseMaterials, setCourseMaterials] = useState<CourseTeachingMaterialGroupInfo[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<number | undefined>();
  const [selectedMaterialId, setSelectedMaterialId] = useState<number | undefined>();
  const [quizQuestions, setQuizQuestions] = useState<StudentQuizQuestionInfo[]>([]);
  const [selectedAnswers, setSelectedAnswers] = useState<Record<string, string>>({});
  const [quizResults, setQuizResults] = useState<Record<string, StudentQuizSubmitResultInfo>>({});
  const [loadingQuiz, setLoadingQuiz] = useState(false);
  const [submittingQuiz, setSubmittingQuiz] = useState(false);
  const [recommendedPath, setRecommendedPath] = useState<LearningPathResult | null>(null);
  const [loadingPath, setLoadingPath] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadStats = useCallback(async () => {
    try {
      const currentUserId = currentUser?.id;
      const [graphData, activityData, reportData, recentData, feedbackData, courseData] = await Promise.all([
        knowledgeApi.getGraph(),
        dashboardApi.getActivities(5),
        currentUserId ? studentActivityApi.getReport(currentUserId) : Promise.resolve(null),
        currentUserId ? studentActivityApi.getRecentActivities(currentUserId, undefined, 6) : Promise.resolve([]),
        currentUserId ? alertApi.getStudentFeedback(currentUserId) : Promise.resolve([]),
        currentUserId ? courseApi.getStudentCourses(currentUserId) : Promise.resolve([]),
      ]);
      setTotalNodes(graphData.nodes?.length ?? 0);
      setActivities(activityData ?? []);
      setLearningReport(reportData);
      setRecentLearningActivities(recentData ?? []);
      setFeedbackAlerts(feedbackData ?? []);
      setStudentCourses(courseData ?? []);
      if (!selectedCourseId && courseData && courseData.length > 0) {
        setSelectedCourseId(courseData[0].id);
      }
    } catch {
      // Keep the student home page usable when the backend is unavailable.
    } finally {
      setLoading(false);
    }
  }, [currentUser?.id, selectedCourseId]);

  useEffect(() => {
    if (!selectedCourseId) {
      setCourseMaterials([]);
      setSelectedMaterialId(undefined);
      return;
    }
    courseApi.getMaterials(selectedCourseId)
      .then(materials => {
        setCourseMaterials(materials ?? []);
        setSelectedMaterialId(materials && materials.length > 0 ? materials[0].latestMaterialId : undefined);
      })
      .catch(() => {
        setCourseMaterials([]);
        setSelectedMaterialId(undefined);
      });
  }, [selectedCourseId]);

  useEffect(() => {
    if (!selectedMaterialId) {
      setQuizQuestions([]);
      return;
    }
    setLoadingQuiz(true);
    studentQuizApi.getQuestions(selectedMaterialId)
      .then(questions => {
        setQuizQuestions(questions ?? []);
        setSelectedAnswers({});
        setQuizResults({});
      })
      .catch(() => setQuizQuestions([]))
      .finally(() => setLoadingQuiz(false));
  }, [selectedMaterialId]);

  const loadPathRecommendation = useCallback(async () => {
    if (!currentUser?.id) {
      return;
    }

    setLoadingPath(true);
    try {
      const result = await pathApi.getRecommendedPath(currentUser.id, [], [], 6);
      setRecommendedPath(result);
    } catch {
      // Ignore recommendation failures and keep the current state.
    } finally {
      setLoadingPath(false);
    }
  }, [currentUser?.id]);

  useEffect(() => {
    loadStats();
    loadPathRecommendation();
  }, [loadStats, loadPathRecommendation]);

  const handleViewPath = () => {
    if (recommendedPath?.nodeIds && recommendedPath.nodeIds.length > 0) {
      onChangeView(View.KNOWLEDGE_GRAPH, { highlightNodeIds: recommendedPath.nodeIds });
    } else {
      onChangeView(View.KNOWLEDGE_GRAPH);
    }
  };

  const handleSubmitQuiz = async () => {
    if (!currentUser?.id || !selectedCourseId || !selectedMaterialId || quizQuestions.length === 0) {
      return;
    }
    setSubmittingQuiz(true);
    try {
      const resultEntries = await Promise.all(
        quizQuestions
          .filter(question => selectedAnswers[question.questionId])
          .map(async question => {
            const result = await studentQuizApi.submitAnswer({
              studentId: currentUser.id,
              courseId: selectedCourseId,
              materialId: selectedMaterialId,
              questionIndex: question.questionIndex,
              answer: selectedAnswers[question.questionId],
            });
            return [question.questionId, result] as const;
          })
      );
      setQuizResults(Object.fromEntries(resultEntries));
      const [reportData, recentData, feedbackData] = await Promise.all([
        studentActivityApi.getReport(currentUser.id),
        studentActivityApi.getRecentActivities(currentUser.id, undefined, 6),
        alertApi.getStudentFeedback(currentUser.id),
      ]);
      setLearningReport(reportData);
      setRecentLearningActivities(recentData ?? []);
      setFeedbackAlerts(feedbackData ?? []);
    } catch {
      // Keep submitted answers on screen so the student can retry.
    } finally {
      setSubmittingQuiz(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Text type="secondary">Loading learning space...</Text>
        </Space>
      </div>
    );
  }

  const greeting = (() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  })();

  const displayName = currentUser?.realName || currentUser?.username || 'Student';

  const getActivityIcon = (type: string) => {
    switch (type) {
      case 'UPLOAD': return <CloudUploadOutlined />;
      case 'AI_ANALYSIS': return <RobotOutlined />;
      case 'GRAPH_UPDATE': return <BlockOutlined />;
      case 'ai_ask': return <RobotOutlined />;
      case 'knowledge_view': return <BlockOutlined />;
      case 'material_open': return <BookOutlined />;
      default: return <InfoCircleOutlined />;
    }
  };

  const weeklyEventCount = learningReport?.weeklyTrend?.reduce((sum, item) => sum + item.eventCount, 0) ?? 0;

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
      <div style={{ maxWidth: 1100, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        <div
          style={{
            background: 'linear-gradient(135deg, var(--color-primary) 0%, #722ed1 100%)',
            borderRadius: 20,
            padding: 32,
            color: '#fff',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <div style={{ position: 'absolute', top: -32, right: -32, width: 160, height: 160, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />
          <div style={{ position: 'absolute', bottom: -16, right: -64, width: 128, height: 128, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />

          <div style={{ position: 'relative', zIndex: 10 }}>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 14 }}>{greeting},</Text>
            <Title level={2} style={{ color: '#fff', margin: '4px 0 8px', fontFamily: "'Lexend', sans-serif" }}>
              Welcome back, {displayName}
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.85)', maxWidth: 480, fontSize: 14, marginBottom: 0 }}>
              Your learning dashboard is ready with today's recommended path and curriculum ideology resources.
            </Paragraph>
          </div>

          <Space size={12} style={{ position: 'relative', zIndex: 10, marginTop: 24 }}>
            <Button type="primary" ghost icon={<RobotOutlined />} onClick={() => onChangeView(View.AI_ASSISTANT)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              AI Assistant
            </Button>
            <Button type="primary" ghost icon={<ShareAltOutlined />} onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              Knowledge Graph
            </Button>
            <Button type="primary" ghost icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'rgba(255,255,255,0.15)' }}>
              Course Library
            </Button>
          </Space>
        </div>

        <Row gutter={[24, 24]}>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Today Study Time</Text>}
                value={learningReport?.todayStudyMinutes ?? 0}
                suffix="min"
                prefix={<CompassOutlined style={{ color: 'var(--color-primary)', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Tracked from student learning events</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Total Study Time</Text>}
                value={learningReport?.totalStudyMinutes ?? 0}
                suffix="min"
                prefix={<NodeIndexOutlined style={{ color: '#722ed1', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Aggregated from tracked learning duration</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card bordered={false} bodyStyle={{ padding: 20 }}>
              <Statistic
                title={<Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>Knowledge Views</Text>}
                value={learningReport?.knowledgeViewCount ?? 0}
                suffix="items"
                prefix={<NotificationOutlined style={{ color: '#faad14', marginRight: 8 }} />}
                valueStyle={{ fontFamily: "'Lexend', sans-serif", fontWeight: 'bold' }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Total graph nodes available: {totalNodes}</Text>
            </Card>
          </Col>
        </Row>

        <Card
          bordered={false}
          title="Practice / Quiz"
          extra={<Text type="secondary">Accuracy: {learningReport?.quizCorrectRate ?? 0}%</Text>}
        >
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Row gutter={[12, 12]}>
              <Col xs={24} md={12}>
                <Select
                  placeholder="Select a course"
                  value={selectedCourseId}
                  style={{ width: '100%' }}
                  options={studentCourses.map(course => ({ value: course.id, label: course.name }))}
                  onChange={value => setSelectedCourseId(value)}
                />
              </Col>
              <Col xs={24} md={12}>
                <Select
                  placeholder="Select a material"
                  value={selectedMaterialId}
                  style={{ width: '100%' }}
                  options={courseMaterials.map(material => ({ value: material.latestMaterialId, label: material.displayTitle }))}
                  onChange={value => setSelectedMaterialId(value)}
                />
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Statistic title="Answered" value={learningReport?.quizAnswerCount ?? 0} suffix="items" />
              </Col>
              <Col xs={24} md={8}>
                <Statistic title="Correct" value={learningReport?.correctQuizAnswerCount ?? 0} suffix="items" />
              </Col>
              <Col xs={24} md={8}>
                <Statistic title="Weak Points" value={learningReport?.weakKnowledgePointIds?.length ?? 0} suffix="items" />
              </Col>
            </Row>

            {loadingQuiz ? (
              <div style={{ padding: '24px 0', textAlign: 'center' }}><Spin /></div>
            ) : quizQuestions.length > 0 ? (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                {quizQuestions.slice(0, 3).map((question, index) => {
                  const result = quizResults[question.questionId];
                  return (
                    <div key={question.questionId} style={{ padding: 16, borderRadius: 12, background: 'var(--color-bg-muted)' }}>
                      <Space direction="vertical" style={{ width: '100%' }} size={10}>
                        <Text strong>{index + 1}. {question.stem}</Text>
                        <Radio.Group
                          value={selectedAnswers[question.questionId]}
                          onChange={event => setSelectedAnswers(prev => ({ ...prev, [question.questionId]: event.target.value }))}
                        >
                          <Space direction="vertical">
                            {question.options.map(option => (
                              <Radio key={option} value={option.slice(0, 1).toUpperCase()}>{option}</Radio>
                            ))}
                          </Space>
                        </Radio.Group>
                        {result && (
                          <Alert
                            type={result.correct ? 'success' : 'warning'}
                            showIcon
                            message={result.correct ? 'Correct' : `Incorrect, correct answer: ${result.correctAnswer}`}
                          />
                        )}
                      </Space>
                    </div>
                  );
                })}
                <Button
                  type="primary"
                  loading={submittingQuiz}
                  disabled={quizQuestions.length === 0 || Object.keys(selectedAnswers).length === 0}
                  onClick={handleSubmitQuiz}
                >
                  Submit answers
                </Button>
              </Space>
            ) : (
              <Empty description="No single choice quiz questions in the selected material" />
            )}
          </Space>
        </Card>

        {feedbackAlerts.length > 0 && (
          <Card bordered={false} bodyStyle={{ padding: 20 }}>
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Space>
                <NotificationOutlined style={{ color: 'var(--color-error)' }} />
                <Text strong>Learning feedback</Text>
              </Space>
              <List<StudentAlertRecordInfo>
                dataSource={feedbackAlerts}
                renderItem={alert => (
                  <List.Item
                    style={{ padding: '12px 0' }}
                    actions={[
                      <Button key="assistant" type="link" onClick={() => onChangeView(View.AI_ASSISTANT)}>Ask AI</Button>,
                      <Button key="graph" type="link" onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}>Review graph</Button>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={<Avatar icon={<InfoCircleOutlined />} style={{ backgroundColor: '#fff1f0', color: 'var(--color-error)' }} />}
                      title={<Text strong>{alert.title}</Text>}
                      description={
                        <Space direction="vertical" size={2}>
                          <Text type="secondary" style={{ fontSize: 12 }}>{alert.message}</Text>
                          <Text style={{ fontSize: 12, color: 'var(--color-primary)' }}>{alert.suggestion}</Text>
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            </Space>
          </Card>
        )}

        <Row gutter={[24, 24]}>
          <Col xs={24} lg={14}>
            <Card
              bordered={false}
              style={{ height: '100%' }}
              title={
                <div>
                  <Title level={5} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Personalized Learning Path</Title>
                  <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>Generated from the DAG structure of the knowledge graph</Text>
                </div>
              }
              extra={
                <Tooltip title="Regenerate recommended path">
                  <Button type="text" icon={<SyncOutlined spin={loadingPath} />} onClick={loadPathRecommendation} disabled={loadingPath} />
                </Tooltip>
              }
            >
              {loadingPath ? (
                <div style={{ padding: '48px 0', textAlign: 'center' }}>
                  <Space direction="vertical" align="center" size={8}>
                    <Spin />
                    <Text type="secondary">Generating the recommended path...</Text>
                  </Space>
                </div>
              ) : recommendedPath && recommendedPath.nodeNames.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <List<string>
                    dataSource={recommendedPath.nodeNames}
                    renderItem={(name, index) => (
                      <List.Item style={{ padding: '12px 16px', background: 'var(--color-bg-muted)', borderRadius: 12, marginBottom: 8, border: 'none' }}>
                        <List.Item.Meta
                          avatar={
                            <Avatar size={28} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)', fontWeight: 'bold', fontSize: 13 }}>
                              {index + 1}
                            </Avatar>
                          }
                          title={<Text strong style={{ fontSize: 14 }}>{name}</Text>}
                        />
                        {index < recommendedPath.nodeNames.length - 1 && (
                          <ArrowRightOutlined style={{ color: '#cbd5e1' }} />
                        )}
                      </List.Item>
                    )}
                  />
                  <Button type="primary" size="large" icon={<CompassOutlined />} onClick={handleViewPath} block style={{ borderRadius: 12 }}>
                    View the path in the knowledge graph
                  </Button>
                </div>
              ) : (
                <Empty
                  image={<CompassOutlined style={{ fontSize: 48, color: '#e2e8f0' }} />}
                  description={
                    <Space direction="vertical" size={2}>
                      <Text strong style={{ color: 'var(--color-text-secondary)' }}>No recommended path yet</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>There are currently no recommended nodes in the knowledge graph</Text>
                    </Space>
                  }
                  style={{ padding: '48px 0' }}
                >
                  <Button onClick={() => onChangeView(View.KNOWLEDGE_GRAPH)}>Explore the knowledge graph</Button>
                </Empty>
              )}
            </Card>
          </Col>

          <Col xs={24} lg={10}>
            <Card
              title="Learning Report"
              bordered={false}
              style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
              bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column' }}
            >
              <Space direction="vertical" style={{ width: '100%', flex: 1 }} size={16}>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>7-day learning signal trend</Text>
                  <div style={{ display: 'flex', alignItems: 'end', gap: 6, height: 72, marginTop: 8 }}>
                    {(learningReport?.weeklyTrend ?? []).map(item => (
                      <Tooltip key={item.date} title={`${item.date}: ${item.eventCount} events`}>
                        <div style={{ flex: 1, display: 'flex', alignItems: 'end', height: '100%' }}>
                          <div
                            style={{
                              width: '100%',
                              minHeight: 4,
                              height: `${weeklyEventCount > 0 ? Math.max(8, item.eventCount * 64 / Math.max(...(learningReport?.weeklyTrend ?? []).map(trend => trend.eventCount), 1)) : 4}px`,
                              borderRadius: 6,
                              background: item.eventCount > 0 ? 'var(--color-primary)' : '#e2e8f0',
                            }}
                          />
                        </div>
                      </Tooltip>
                    ))}
                  </div>
                </div>

                {recentLearningActivities.length > 0 ? (
                  <List<StudentRecentActivityInfo>
                    itemLayout="horizontal"
                    dataSource={recentLearningActivities}
                    renderItem={activity => (
                      <List.Item style={{ borderBottom: '1px solid var(--color-bg-muted)' }}>
                        <List.Item.Meta
                          avatar={
                            <Avatar icon={getActivityIcon(activity.eventType)} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }} />
                          }
                          title={<Text strong style={{ fontSize: 14 }}>{activity.title}</Text>}
                          description={
                            <Space direction="vertical" size={2}>
                              <Text type="secondary" ellipsis style={{ fontSize: 12, maxWidth: 220 }}>{activity.description}</Text>
                              <Text type="secondary" style={{ fontSize: 11, opacity: 0.8 }}>{activity.occurredAt}</Text>
                            </Space>
                          }
                        />
                      </List.Item>
                    )}
                  />
                ) : activities.length > 0 ? (
                  <List<ActivityInfo>
                    itemLayout="horizontal"
                    dataSource={activities}
                    renderItem={activity => (
                      <List.Item style={{ borderBottom: '1px solid var(--color-bg-muted)' }}>
                        <List.Item.Meta
                          avatar={<Avatar icon={getActivityIcon(activity.type)} style={{ backgroundColor: 'var(--color-primary-soft)', color: 'var(--color-primary)' }} />}
                          title={<Text strong style={{ fontSize: 14 }}>{activity.title}</Text>}
                          description={<Text type="secondary" ellipsis style={{ fontSize: 12, maxWidth: 220 }}>{activity.description}</Text>}
                        />
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty description="No learning records yet" style={{ padding: '32px 0' }} />
                )}
              </Space>
              <div style={{ marginTop: 16 }}>
                <Button type="dashed" block icon={<BookOutlined />} onClick={() => onChangeView(View.COURSE_LIBRARY)} style={{ borderRadius: 12 }}>
                  Browse the course library
                </Button>
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};
