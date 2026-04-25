import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, TeamOutlined, UserAddOutlined } from '@ant-design/icons';
import {
  adminApi,
  dashboardApi,
  AdminUserInfo,
  AdminUserRequest,
  CourseInfo,
  CourseStudentInfo,
} from '../services/api';

const { Text, Title } = Typography;

const roleOptions = [
  { label: 'Admin', value: 'ADMIN' },
  { label: 'Teacher', value: 'TEACHER' },
  { label: 'Student', value: 'STUDENT' },
];

const formatTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

export const AdminConsole: React.FC = () => {
  const [users, setUsers] = useState<AdminUserInfo[]>([]);
  const [students, setStudents] = useState<AdminUserInfo[]>([]);
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [bindings, setBindings] = useState<CourseStudentInfo[]>([]);
  const [userTotal, setUserTotal] = useState(0);
  const [userPage, setUserPage] = useState(1);
  const [userSize, setUserSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState<string | undefined>();
  const [selectedCourseId, setSelectedCourseId] = useState<number | undefined>();
  const [selectedStudentId, setSelectedStudentId] = useState<number | undefined>();
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [loadingBindings, setLoadingBindings] = useState(false);
  const [savingUser, setSavingUser] = useState(false);
  const [bindingStudent, setBindingStudent] = useState(false);
  const [updatingStatusId, setUpdatingStatusId] = useState<number | null>(null);
  const [removingStudentId, setRemovingStudentId] = useState<number | null>(null);
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUserInfo | null>(null);
  const [userForm] = Form.useForm();

  const studentOptions = useMemo(() => {
    const boundIds = new Set(bindings.map((item) => item.studentId));
    return students
      .filter((student) => !boundIds.has(student.id))
      .map((student) => ({
        label: `${student.realName || student.username} (${student.username})`,
        value: student.id,
      }));
  }, [bindings, students]);

  const loadUsers = async (page = userPage, size = userSize) => {
    setLoadingUsers(true);
    try {
      const result = await adminApi.listUsers({
        keyword: keyword.trim(),
        role: roleFilter,
        page,
        size,
      });
      setUsers(result.records || []);
      setUserTotal(result.total || 0);
      setUserPage(result.page || page);
      setUserSize(result.size || size);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to load users');
    } finally {
      setLoadingUsers(false);
    }
  };

  const loadStudents = async () => {
    try {
      const result = await adminApi.listUsers({ role: 'STUDENT', page: 1, size: 100 });
      setStudents(result.records || []);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to load students');
    }
  };

  const loadCourses = async () => {
    try {
      const data = await dashboardApi.getCourses();
      setCourses(data || []);
      if (!selectedCourseId && data?.length) {
        setSelectedCourseId(data[0].id);
      }
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to load courses');
    }
  };

  const loadBindings = async (courseId?: number) => {
    if (!courseId) {
      setBindings([]);
      return;
    }
    setLoadingBindings(true);
    try {
      const data = await adminApi.listCourseStudents(courseId);
      setBindings(data || []);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to load course students');
    } finally {
      setLoadingBindings(false);
    }
  };

  useEffect(() => {
    Promise.all([loadUsers(1, userSize), loadStudents(), loadCourses()]);
  }, []);

  useEffect(() => {
    loadBindings(selectedCourseId);
  }, [selectedCourseId]);

  const openCreateUser = () => {
    setEditingUser(null);
    userForm.resetFields();
    userForm.setFieldsValue({ role: 'STUDENT' });
    setUserModalOpen(true);
  };

  const openEditUser = (user: AdminUserInfo) => {
    setEditingUser(user);
    userForm.setFieldsValue({
      username: user.username,
      email: user.email || '',
      realName: user.realName || '',
      role: user.role,
      department: user.department || '',
      password: '',
    });
    setUserModalOpen(true);
  };

  const saveUser = async () => {
    const values = await userForm.validateFields();
    const payload: AdminUserRequest = {
      username: values.username,
      password: values.password,
      email: values.email || '',
      realName: values.realName || '',
      role: values.role,
      department: values.department || '',
    };
    setSavingUser(true);
    try {
      if (editingUser) {
        await adminApi.updateUser(editingUser.id, payload);
        message.success('User updated');
      } else {
        await adminApi.createUser(payload);
        message.success('User created');
      }
      setUserModalOpen(false);
      userForm.resetFields();
      await Promise.all([loadUsers(userPage, userSize), loadStudents()]);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to save user');
    } finally {
      setSavingUser(false);
    }
  };

  const updateUserStatus = async (user: AdminUserInfo, enabled: boolean) => {
    setUpdatingStatusId(user.id);
    try {
      await adminApi.updateUserStatus(user.id, enabled ? 1 : 0);
      message.success('User status updated');
      await Promise.all([loadUsers(userPage, userSize), loadStudents()]);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to update user status');
    } finally {
      setUpdatingStatusId(null);
    }
  };

  const addCourseStudent = async () => {
    if (!selectedCourseId || !selectedStudentId) {
      message.warning('Select a course and student first');
      return;
    }
    setBindingStudent(true);
    try {
      await adminApi.addCourseStudent(selectedCourseId, selectedStudentId);
      message.success('Student bound to course');
      setSelectedStudentId(undefined);
      await loadBindings(selectedCourseId);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to bind student');
    } finally {
      setBindingStudent(false);
    }
  };

  const removeCourseStudent = async (studentId: number) => {
    if (!selectedCourseId) {
      return;
    }
    setRemovingStudentId(studentId);
    try {
      await adminApi.removeCourseStudent(selectedCourseId, studentId);
      message.success('Student removed from course');
      await loadBindings(selectedCourseId);
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : 'Failed to remove student');
    } finally {
      setRemovingStudentId(null);
    }
  };

  if (loadingUsers && courses.length === 0) {
    return (
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
      <div style={{ maxWidth: 1180, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Space align="start" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space direction="vertical" size={2}>
            <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif" }}>Admin Console</Title>
            <Text type="secondary">Manage users and course-student bindings for the learning workspace.</Text>
          </Space>
          <Button icon={<ReloadOutlined />} onClick={() => Promise.all([loadUsers(userPage, userSize), loadStudents(), loadCourses(), loadBindings(selectedCourseId)])}>
            Refresh
          </Button>
        </Space>

        <Tabs
          items={[
            {
              key: 'users',
              label: 'Users',
              children: (
                <Card
                  title={<Space><TeamOutlined /> <Text strong>Users</Text></Space>}
                  extra={<Button type="primary" icon={<UserAddOutlined />} onClick={openCreateUser}>New User</Button>}
                >
                  <Space style={{ marginBottom: 16 }} wrap>
                    <Input.Search
                      placeholder="Search username, name or email"
                      value={keyword}
                      onChange={(event) => setKeyword(event.target.value)}
                      onSearch={() => loadUsers(1, userSize)}
                      allowClear
                      style={{ width: 260 }}
                    />
                    <Select
                      allowClear
                      placeholder="Role"
                      value={roleFilter}
                      options={roleOptions}
                      style={{ width: 160 }}
                      onChange={(value) => setRoleFilter(value)}
                    />
                    <Button onClick={() => loadUsers(1, userSize)}>Filter</Button>
                  </Space>
                  <Table<AdminUserInfo>
                    rowKey="id"
                    loading={loadingUsers}
                    dataSource={users}
                    pagination={{
                      current: userPage,
                      pageSize: userSize,
                      total: userTotal,
                      onChange: (page, size) => loadUsers(page, size),
                    }}
                    columns={[
                      {
                        title: 'User',
                        dataIndex: 'username',
                        render: (_, user) => (
                          <Space direction="vertical" size={0}>
                            <Text strong>{user.realName || user.username}</Text>
                            <Text type="secondary">{user.username}</Text>
                          </Space>
                        ),
                      },
                      { title: 'Email', dataIndex: 'email', render: (value?: string) => value || '-' },
                      { title: 'Role', dataIndex: 'role', render: (role: string) => <Tag color={role === 'ADMIN' ? 'purple' : role === 'TEACHER' ? 'blue' : 'green'}>{role}</Tag> },
                      { title: 'Department', dataIndex: 'department', render: (value?: string) => value || '-' },
                      {
                        title: 'Enabled',
                        dataIndex: 'status',
                        render: (_, user) => (
                          <Switch
                            checked={user.status === 1}
                            loading={updatingStatusId === user.id}
                            onChange={(checked) => updateUserStatus(user, checked)}
                          />
                        ),
                      },
                      { title: 'Updated', dataIndex: 'updatedAt', render: formatTime },
                      {
                        title: 'Actions',
                        render: (_, user) => <Button size="small" onClick={() => openEditUser(user)}>Edit</Button>,
                      },
                    ]}
                  />
                </Card>
              ),
            },
            {
              key: 'bindings',
              label: 'Course Students',
              children: (
                <Card title={<Space><TeamOutlined /> <Text strong>Course Students</Text></Space>}>
                  <Space style={{ marginBottom: 16 }} wrap>
                    <Select
                      placeholder="Select course"
                      value={selectedCourseId}
                      style={{ width: 300 }}
                      showSearch
                      optionFilterProp="label"
                      options={courses.map((course) => ({ label: course.name, value: course.id }))}
                      onChange={(value) => setSelectedCourseId(value)}
                    />
                    <Select
                      placeholder="Select student"
                      value={selectedStudentId}
                      style={{ width: 300 }}
                      showSearch
                      allowClear
                      optionFilterProp="label"
                      options={studentOptions}
                      onChange={(value) => setSelectedStudentId(value)}
                    />
                    <Button type="primary" loading={bindingStudent} onClick={addCourseStudent}>
                      Add Student
                    </Button>
                  </Space>

                  {selectedCourseId ? (
                    <Table<CourseStudentInfo>
                      rowKey="studentId"
                      loading={loadingBindings}
                      dataSource={bindings}
                      pagination={false}
                      columns={[
                        {
                          title: 'Student',
                          dataIndex: 'username',
                          render: (_, student) => (
                            <Space direction="vertical" size={0}>
                              <Text strong>{student.realName || student.username}</Text>
                              <Text type="secondary">{student.username}</Text>
                            </Space>
                          ),
                        },
                        { title: 'Email', dataIndex: 'email', render: (value?: string) => value || '-' },
                        { title: 'Department', dataIndex: 'department', render: (value?: string) => value || '-' },
                        { title: 'Bound At', dataIndex: 'boundAt', render: formatTime },
                        {
                          title: 'Actions',
                          render: (_, student) => (
                            <Button
                              size="small"
                              danger
                              loading={removingStudentId === student.studentId}
                              onClick={() => removeCourseStudent(student.studentId)}
                            >
                              Remove
                            </Button>
                          ),
                        },
                      ]}
                    />
                  ) : (
                    <Empty description="Select a course to manage students." image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </Card>
              ),
            },
          ]}
        />
      </div>

      <Modal
        title={editingUser ? 'Edit User' : 'New User'}
        open={userModalOpen}
        onOk={saveUser}
        onCancel={() => setUserModalOpen(false)}
        confirmLoading={savingUser}
        destroyOnClose
      >
        <Form form={userForm} layout="vertical" preserve={false} initialValues={{ role: 'STUDENT' }}>
          <Form.Item name="username" label="Username" rules={[{ required: !editingUser, message: 'Username is required' }]}>
            <Input disabled={Boolean(editingUser)} placeholder="student_new" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[{ required: !editingUser, min: 6, message: 'Password must be at least 6 characters' }]}
          >
            <Input.Password placeholder={editingUser ? 'Leave blank to keep current password' : 'At least 6 characters'} />
          </Form.Item>
          <Form.Item name="role" label="Role" rules={[{ required: true, message: 'Role is required' }]}>
            <Select options={roleOptions} />
          </Form.Item>
          <Form.Item name="realName" label="Real Name">
            <Input placeholder="Student Chen" />
          </Form.Item>
          <Form.Item name="email" label="Email">
            <Input placeholder="student@example.com" />
          </Form.Item>
          <Form.Item name="department" label="Department">
            <Input placeholder="IoT" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
