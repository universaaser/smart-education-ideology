import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { FileSearchOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { dashboardApi, CourseInfo, keywordTaskApi, KeywordTaskInfo, KeywordTaskItemInfo } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Text, Title, Paragraph } = Typography;

const formatTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
};

const statusColor = (status?: string) => {
  switch (status) {
    case 'DONE':
    case 'ACCEPTED':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'PENDING':
    case 'RUNNING':
      return 'orange';
    case 'SKIPPED':
      return 'default';
    default:
      return 'blue';
  }
};

const splitKeywords = (value?: string) =>
  (value || '')
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean);

export const KeywordTasks: React.FC = () => {
  const { currentUser } = useAuth();
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [tasks, setTasks] = useState<KeywordTaskInfo[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<number | undefined>();
  const [selectedTask, setSelectedTask] = useState<KeywordTaskInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [taskLoading, setTaskLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [runningTaskId, setRunningTaskId] = useState<number | null>(null);
  const [acceptingItemId, setAcceptingItemId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const courseOptions = useMemo(
    () => courses.map((course) => ({ label: course.name, value: course.id })),
    [courses],
  );

  const loadTasks = async (courseId = selectedCourseId) => {
    setTaskLoading(true);
    try {
      const data = await keywordTaskApi.listTasks(courseId);
      setTasks(data || []);
      if (selectedTask) {
        const refreshed = data.find((task) => task.id === selectedTask.id);
        setSelectedTask(refreshed || null);
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load keyword tasks');
    } finally {
      setTaskLoading(false);
    }
  };

  useEffect(() => {
    const loadInitialData = async () => {
      setLoading(true);
      try {
        const courseData = await dashboardApi.getCourses();
        setCourses(courseData || []);
        const initialCourseId = courseData?.[0]?.id;
        setSelectedCourseId(initialCourseId);
        form.setFieldsValue({ courseId: initialCourseId });
        const taskData = await keywordTaskApi.listTasks(initialCourseId);
        setTasks(taskData || []);
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : 'Failed to load keyword task data');
      } finally {
        setLoading(false);
      }
    };
    loadInitialData();
  }, [form]);

  const createTask = async () => {
    const values = await form.validateFields();
    const keywords = splitKeywords(values.keywords);
    if (keywords.length === 0) {
      message.error('Keywords cannot be empty');
      return;
    }
    setCreating(true);
    try {
      const task = await keywordTaskApi.createTask({
        courseId: values.courseId,
        creatorId: currentUser?.id,
        keywords,
      });
      message.success('Keyword task created');
      setSelectedCourseId(values.courseId);
      setSelectedTask(task);
      form.setFieldValue('keywords', '');
      await loadTasks(values.courseId);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to create keyword task');
    } finally {
      setCreating(false);
    }
  };

  const runTask = async (task: KeywordTaskInfo) => {
    setRunningTaskId(task.id);
    try {
      const updated = await keywordTaskApi.runTask(task.id);
      message.success('Keyword task finished');
      setSelectedTask(updated);
      await loadTasks(selectedCourseId);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to run keyword task');
    } finally {
      setRunningTaskId(null);
    }
  };

  const openTaskDetail = async (task: KeywordTaskInfo) => {
    setTaskLoading(true);
    try {
      const detail = await keywordTaskApi.getTask(task.id);
      setSelectedTask(detail);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to load task detail');
    } finally {
      setTaskLoading(false);
    }
  };

  const acceptItem = async (item: KeywordTaskItemInfo) => {
    setAcceptingItemId(item.id);
    try {
      await keywordTaskApi.acceptItem(item.taskId, item.id);
      message.success('Keyword result accepted');
      const detail = await keywordTaskApi.getTask(item.taskId);
      setSelectedTask(detail);
      await loadTasks(selectedCourseId);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : 'Failed to accept keyword result');
    } finally {
      setAcceptingItemId(null);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
      <div style={{ maxWidth: 1120, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Space align="start" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space direction="vertical" size={2}>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Keyword Tasks</Title>
            <Text type="secondary">Collect teaching resources from approved knowledge sources by course keywords.</Text>
          </Space>
          <Button icon={<ReloadOutlined />} loading={taskLoading} onClick={() => loadTasks()}>
            Refresh
          </Button>
        </Space>

        <Card title={<Space><PlusOutlined /> <Text strong>New Keyword Task</Text></Space>}>
          <Form form={form} layout="vertical">
            <Form.Item name="courseId" label="Course" rules={[{ required: true, message: 'Course is required' }]}>
              <Select
                placeholder="Select a course"
                options={courseOptions}
                onChange={(value) => {
                  setSelectedCourseId(value);
                  loadTasks(value);
                }}
              />
            </Form.Item>
            <Form.Item name="keywords" label="Keywords" rules={[{ required: true, message: 'Keywords cannot be empty' }]}>
              <Input.TextArea rows={3} placeholder="edge computing, smart sensor" />
            </Form.Item>
            <Button type="primary" icon={<FileSearchOutlined />} loading={creating} onClick={createTask}>
              Create Task
            </Button>
          </Form>
        </Card>

        <Card title={<Space><FileSearchOutlined /> <Text strong>Task List</Text></Space>}>
          <Table<KeywordTaskInfo>
            rowKey="id"
            loading={taskLoading}
            dataSource={tasks}
            pagination={{ pageSize: 6 }}
            columns={[
              { title: 'ID', dataIndex: 'id', width: 80 },
              { title: 'Keywords', dataIndex: 'keywords', render: (keywords: string[]) => keywords?.join(', ') || '-' },
              { title: 'Status', dataIndex: 'status', render: (status: string) => <Tag color={statusColor(status)}>{status}</Tag> },
              { title: 'Summary', dataIndex: 'resultSummary', ellipsis: true },
              { title: 'Created', dataIndex: 'createdAt', render: formatTime },
              {
                title: 'Actions',
                width: 220,
                render: (_, task) => (
                  <Space>
                    <Button size="small" onClick={() => openTaskDetail(task)}>Detail</Button>
                    <Button
                      size="small"
                      type="primary"
                      icon={<PlayCircleOutlined />}
                      loading={runningTaskId === task.id}
                      disabled={task.status === 'RUNNING'}
                      onClick={() => runTask(task)}
                    >
                      Run
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card title={<Space><FileSearchOutlined /> <Text strong>Task Detail</Text></Space>}>
          {!selectedTask ? (
            <Empty description="Select a keyword task to inspect generated items." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Space wrap>
                <Text strong>Task #{selectedTask.id}</Text>
                <Tag color={statusColor(selectedTask.status)}>{selectedTask.status}</Tag>
                <Text type="secondary">Keywords: {selectedTask.keywords?.join(', ') || '-'}</Text>
              </Space>
              {selectedTask.errorSummary && <Text type="danger">{selectedTask.errorSummary}</Text>}
              {selectedTask.items?.length ? (
                <List
                  dataSource={selectedTask.items}
                  renderItem={(item) => (
                    <List.Item
                      actions={[
                        <Button
                          key="accept"
                          size="small"
                          type="primary"
                          loading={acceptingItemId === item.id}
                          disabled={item.status === 'ACCEPTED' || item.status === 'SKIPPED'}
                          onClick={() => acceptItem(item)}
                        >
                          Accept
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        title={(
                          <Space wrap>
                            <Text strong>{item.title}</Text>
                            <Tag>{item.keyword}</Tag>
                            <Tag color={statusColor(item.status)}>{item.status}</Tag>
                          </Space>
                        )}
                        description={(
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>{item.aiSummary || item.excerpt || '-'}</Paragraph>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              URL: {item.sourceUrl || '-'} · Created: {formatTime(item.createdAt)}
                            </Text>
                          </Space>
                        )}
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="No generated keyword items yet. Run the task first." image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Space>
          )}
        </Card>
      </div>
    </div>
  );
};
