import React, { useEffect, useMemo, useState } from 'react';
import { View, ViewChangeHandler } from '../types';
import {
  courseApi,
  CourseChapterInfo,
  CourseInfo,
  CourseStatusSummaryInfo,
  CourseStudentInfo,
  CourseTeachingMaterialGroupInfo,
  KnowledgeNodeInfo,
  StudentOptionInfo,
} from '../services/api';
import {
  ArrowLeftOutlined,
  BookOutlined,
  FileTextOutlined,
  SaveOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  message,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';

const { Text, Title } = Typography;

interface CourseManagementProps {
  courseId: number | null;
  onChangeView: ViewChangeHandler;
}

export const CourseManagement: React.FC<CourseManagementProps> = ({ courseId, onChangeView }) => {
  const [form] = Form.useForm();
  const [course, setCourse] = useState<CourseInfo | null>(null);
  const [summary, setSummary] = useState<CourseStatusSummaryInfo | null>(null);
  const [chapters, setChapters] = useState<CourseChapterInfo[]>([]);
  const [materials, setMaterials] = useState<CourseTeachingMaterialGroupInfo[]>([]);
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgeNodeInfo[]>([]);
  const [students, setStudents] = useState<CourseStudentInfo[]>([]);
  const [studentOptions, setStudentOptions] = useState<StudentOptionInfo[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [binding, setBinding] = useState(false);

  const loadCourseData = async () => {
    if (!courseId) {
      return;
    }
    setLoading(true);
    try {
      const [courseData, summaryData, chapterData, materialData, knowledgeData, studentData, optionData] = await Promise.all([
        courseApi.getById(courseId),
        courseApi.getStatusSummary(courseId),
        courseApi.getChapters(courseId),
        courseApi.getMaterials(courseId),
        courseApi.getKnowledgePoints(courseId),
        courseApi.listCourseStudents(courseId),
        courseApi.listStudentOptions(),
      ]);
      setCourse(courseData);
      setSummary(summaryData);
      setChapters(chapterData || []);
      setMaterials(materialData || []);
      setKnowledgePoints(knowledgeData || []);
      setStudents(studentData || []);
      setStudentOptions(optionData || []);
      form.setFieldsValue({
        name: courseData.name,
        code: courseData.code,
        description: courseData.description,
        teacherId: courseData.teacherId,
        semester: courseData.semester,
        progress: courseData.progress,
        ideologyScore: courseData.ideologyScore,
        coverImage: courseData.coverImage,
        status: courseData.status,
      });
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to load course');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCourseData();
  }, [courseId]);

  const availableStudents = useMemo(() => {
    const boundIds = new Set(students.map(student => student.studentId));
    return studentOptions.filter(student => !boundIds.has(student.id));
  }, [studentOptions, students]);

  const saveCourse = async () => {
    if (!courseId) {
      return;
    }
    try {
      const values = await form.validateFields();
      setSaving(true);
      const updated = await courseApi.update(courseId, values);
      setCourse(updated);
      message.success('Course saved');
    } catch (error: unknown) {
      if (error instanceof Error) {
        message.error(error.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const addStudent = async () => {
    if (!courseId || !selectedStudentId) {
      return;
    }
    setBinding(true);
    try {
      await courseApi.addCourseStudent(courseId, selectedStudentId);
      setSelectedStudentId(null);
      const nextStudents = await courseApi.listCourseStudents(courseId);
      setStudents(nextStudents || []);
      message.success('Student added');
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to add student');
    } finally {
      setBinding(false);
    }
  };

  const removeStudent = async (studentId: number) => {
    if (!courseId) {
      return;
    }
    setBinding(true);
    try {
      await courseApi.removeCourseStudent(courseId, studentId);
      setStudents(previous => previous.filter(student => student.studentId !== studentId));
      message.success('Student removed');
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to remove student');
    } finally {
      setBinding(false);
    }
  };

  if (!courseId) {
    return (
      <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
        <Card bordered={false}>
          <Empty description="Select a course from the dashboard to manage it.">
            <Button onClick={() => onChangeView(View.DASHBOARD)}>Back to dashboard</Button>
          </Empty>
        </Card>
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Space direction="vertical" align="center"><Spin size="large" /><Text type="secondary">Loading course management...</Text></Space>
      </div>
    );
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
      <div style={{ maxWidth: 1180, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Space style={{ justifyContent: 'space-between', width: '100%' }} align="start">
          <Space direction="vertical" size={4}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => onChangeView(View.DASHBOARD)}>Back</Button>
            <Title level={3} style={{ margin: 0 }}>{course?.name || 'Course Management'}</Title>
            <Text type="secondary">Edit course information, review linked content, and manage enrolled students.</Text>
          </Space>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveCourse}>Save course</Button>
        </Space>

        <Tabs
          items={[
            {
              key: 'overview',
              label: 'Overview',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={16}>
                  <Row gutter={[16, 16]}>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Chapters" value={summary?.chapterCount ?? 0} /></Card></Col>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Materials" value={summary?.materialCount ?? 0} /></Card></Col>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Drafts" value={summary?.draftMaterialCount ?? 0} /></Card></Col>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Published" value={summary?.publishedMaterialCount ?? 0} /></Card></Col>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Knowledge" value={summary?.knowledgePointCount ?? 0} /></Card></Col>
                    <Col xs={12} md={4}><Card bordered={false}><Statistic title="Parse Tasks" value={summary?.parseTaskCount ?? 0} /></Card></Col>
                  </Row>
                  <Card bordered={false} title="Course Information">
                    <Form form={form} layout="vertical">
                      <Row gutter={16}>
                        <Col xs={24} md={12}><Form.Item name="name" label="Name" rules={[{ required: true, message: 'Course name is required' }]}><Input /></Form.Item></Col>
                        <Col xs={24} md={12}><Form.Item name="code" label="Code"><Input /></Form.Item></Col>
                        <Col xs={24} md={12}><Form.Item name="semester" label="Semester"><Input /></Form.Item></Col>
                        <Col xs={24} md={12}><Form.Item name="teacherId" label="Teacher ID"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item name="progress" label="Progress"><InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item name="ideologyScore" label="Ideology Score"><Select options={[{ value: 'EXCELLENT' }, { value: 'GOOD' }, { value: 'FAIR' }, { value: 'POOR' }]} /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item name="status" label="Status"><Select options={[{ value: 0, label: 'Draft' }, { value: 1, label: 'Published' }, { value: 2, label: 'Archived' }]} /></Form.Item></Col>
                        <Col xs={24}><Form.Item name="coverImage" label="Cover Image"><Input /></Form.Item></Col>
                        <Col xs={24}><Form.Item name="description" label="Description"><Input.TextArea rows={4} /></Form.Item></Col>
                      </Row>
                    </Form>
                  </Card>
                </Space>
              ),
            },
            {
              key: 'content',
              label: 'Course Content',
              children: (
                <Row gutter={[16, 16]}>
                  <Col xs={24} lg={8}>
                    <Card bordered={false} title={<Space><BookOutlined />Chapters</Space>}>
                      <List dataSource={chapters} locale={{ emptyText: 'No chapters' }} renderItem={chapter => (
                        <List.Item><Space direction="vertical" size={2}><Text strong>{chapter.title}</Text><Text type="secondary">Sort order {chapter.sortOrder}</Text></Space></List.Item>
                      )} />
                    </Card>
                  </Col>
                  <Col xs={24} lg={8}>
                    <Card bordered={false} title={<Space><FileTextOutlined />Materials</Space>}>
                      <List dataSource={materials} locale={{ emptyText: 'No materials' }} renderItem={group => (
                        <List.Item>
                          <Space direction="vertical" size={2}>
                            <Text strong>{group.displayTitle || group.sourceFileName || `Task ${group.parseTaskId}`}</Text>
                            <Space><Tag color="blue">v{group.latestVersionNo || '-'}</Tag><Tag>{group.latestStatus || 'UNKNOWN'}</Tag></Space>
                          </Space>
                        </List.Item>
                      )} />
                    </Card>
                  </Col>
                  <Col xs={24} lg={8}>
                    <Card bordered={false} title="Knowledge Points">
                      <List dataSource={knowledgePoints} locale={{ emptyText: 'No knowledge points' }} renderItem={point => (
                        <List.Item><Space direction="vertical" size={2}><Text strong>{point.name}</Text><Text type="secondary" ellipsis>{point.technicalDefinition || point.ideologicalValue}</Text></Space></List.Item>
                      )} />
                    </Card>
                  </Col>
                </Row>
              ),
            },
            {
              key: 'students',
              label: 'Students',
              children: (
                <Card bordered={false} title={<Space><TeamOutlined />Course Students</Space>}>
                  <Space style={{ marginBottom: 16 }} wrap>
                    <Select
                      showSearch
                      allowClear
                      placeholder="Select student"
                      style={{ width: 320 }}
                      value={selectedStudentId ?? undefined}
                      onChange={value => setSelectedStudentId(value ?? null)}
                      optionFilterProp="label"
                      options={availableStudents.map(student => ({
                        value: student.id,
                        label: `${student.realName || student.username} (${student.username})`,
                      }))}
                    />
                    <Button type="primary" loading={binding} disabled={!selectedStudentId} onClick={addStudent}>Add student</Button>
                  </Space>
                  <Table<CourseStudentInfo>
                    rowKey="studentId"
                    dataSource={students}
                    pagination={false}
                    columns={[
                      { title: 'Name', render: (_, record) => record.realName || record.username },
                      { title: 'Username', dataIndex: 'username' },
                      { title: 'Email', dataIndex: 'email' },
                      { title: 'Department', dataIndex: 'department' },
                      { title: 'Action', align: 'right', render: (_, record) => <Button danger type="link" loading={binding} onClick={() => removeStudent(record.studentId)}>Remove</Button> },
                    ]}
                  />
                </Card>
              ),
            },
          ]}
        />
      </div>
    </div>
  );
};
