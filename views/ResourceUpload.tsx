import React, { useState, useCallback } from 'react';
import { uploadApi, UploadTaskInfo } from '../services/api';

interface ResourceUploadProps {
  userId?: number;
}

type UploadStatus = 'idle' | 'uploading' | 'parsing' | 'completed' | 'failed';

export const ResourceUpload: React.FC<ResourceUploadProps> = ({ userId = 1 }) => {
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<UploadStatus>('idle');
  const [progress, setProgress] = useState(0);
  const [currentStep, setCurrentStep] = useState('');
  const [taskResult, setTaskResult] = useState<UploadTaskInfo | null>(null);
  const [error, setError] = useState('');
  const [isDragOver, setIsDragOver] = useState(false);

  /** 处理文件选择 */
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      setFile(selectedFile);
      setStatus('idle');
      setError('');
      setTaskResult(null);
    }
  };

  /** 处理拖拽上传 */
  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const droppedFile = e.dataTransfer.files?.[0];
    if (droppedFile) {
      setFile(droppedFile);
      setStatus('idle');
      setError('');
      setTaskResult(null);
    }
  }, []);

  /**
   * 轮询解析任务状态
   * 每 2 秒查询一次，直到任务完成或失败
   */
  const pollTaskStatus = async (taskId: number) => {
    const poll = async () => {
      try {
        const taskInfo = await uploadApi.getTaskStatus(taskId);
        setProgress(taskInfo.progress);
        setCurrentStep(taskInfo.currentStep);

        if (taskInfo.status === 'COMPLETED') {
          setStatus('completed');
          setTaskResult(taskInfo);
          return;
        }

        if (taskInfo.status === 'FAILED') {
          setStatus('failed');
          setError(taskInfo.errorMessage || '解析失败');
          return;
        }

        // 继续轮询
        setTimeout(poll, 2000);
      } catch {
        setStatus('failed');
        setError('获取任务状态失败');
      }
    };
    poll();
  };

  /** 执行文件上传 */
  const handleUpload = async () => {
    if (!file) return;

    setStatus('uploading');
    setProgress(0);
    setError('');
    setCurrentStep('正在上传文件...');

    try {
      const result = await uploadApi.uploadFile(file, userId);
      setStatus('parsing');
      setProgress(10);
      setCurrentStep('文件已上传，正在解析...');
      // 开始轮询任务状态
      pollTaskStatus(result.taskId);
    } catch (err: unknown) {
      setStatus('failed');
      setError(err instanceof Error ? err.message : '上传失败');
    }
  };

  /** 重置状态，允许重新上传 */
  const handleReset = () => {
    setFile(null);
    setStatus('idle');
    setProgress(0);
    setCurrentStep('');
    setTaskResult(null);
    setError('');
  };

  /** 格式化文件大小 */
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  return (
    <div className="flex-1 overflow-y-auto p-8 bg-background-light">
      <div className="max-w-3xl mx-auto space-y-8">
        {/* 标题 */}
        <div>
          <h2 className="text-2xl font-display font-bold text-slate-900">智能文档解析</h2>
          <p className="text-slate-500 mt-1">上传教学文档，AI 自动提取知识点并挖掘思政元素</p>
        </div>

        {/* 上传区域 */}
        <div
          onDragOver={(e) => { e.preventDefault(); setIsDragOver(true); }}
          onDragLeave={() => setIsDragOver(false)}
          onDrop={handleDrop}
          className={`relative bg-white border-2 border-dashed rounded-2xl p-12 text-center transition-all ${isDragOver
              ? 'border-primary bg-primary/5 scale-[1.01]'
              : 'border-slate-200 hover:border-primary/50'
            } ${status !== 'idle' ? 'opacity-50 pointer-events-none' : ''}`}
        >
          <input
            type="file"
            accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx"
            onChange={handleFileChange}
            className="absolute inset-0 opacity-0 cursor-pointer"
            disabled={status !== 'idle'}
          />
          <div className="flex flex-col items-center gap-4">
            <div className="size-16 bg-primary/10 rounded-2xl flex items-center justify-center">
              <span className="material-symbols-outlined text-primary text-3xl">cloud_upload</span>
            </div>
            <div>
              <p className="text-lg font-medium text-slate-700">
                拖拽文件到此处，或 <span className="text-primary">点击浏览</span>
              </p>
              <p className="text-sm text-slate-400 mt-2">支持 PDF、Word、PPT、Excel 格式，最大 50MB</p>
            </div>
          </div>
        </div>

        {/* 已选文件信息 */}
        {file && status === 'idle' && (
          <div className="bg-white border border-slate-100 rounded-xl p-4 flex items-center justify-between shadow-sm">
            <div className="flex items-center gap-4">
              <div className="p-2 bg-blue-50 rounded-lg">
                <span className="material-symbols-outlined text-primary text-xl">description</span>
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">{file.name}</p>
                <p className="text-xs text-slate-400">{formatFileSize(file.size)}</p>
              </div>
            </div>
            <button
              onClick={handleUpload}
              className="flex items-center gap-2 px-5 py-2.5 bg-primary text-white rounded-xl text-sm font-bold hover:bg-blue-700 transition-colors shadow-sm shadow-primary/20"
            >
              <span className="material-symbols-outlined text-[18px]">play_arrow</span>
              开始解析
            </button>
          </div>
        )}

        {/* 解析进度 */}
        {(status === 'uploading' || status === 'parsing') && (
          <div className="bg-white border border-slate-100 rounded-xl p-6 shadow-sm space-y-4">
            <div className="flex items-center gap-3">
              <div className="size-8 border-3 border-primary/20 border-t-primary rounded-full animate-spin"></div>
              <div>
                <p className="text-sm font-bold text-slate-800">{currentStep || '处理中...'}</p>
                <p className="text-xs text-slate-400 mt-0.5">{file?.name}</p>
              </div>
            </div>
            <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full transition-all duration-500"
                style={{ width: `${progress}%` }}
              ></div>
            </div>
            <p className="text-xs text-slate-400 text-right">{progress}%</p>
          </div>
        )}

        {/* 解析完成 */}
        {status === 'completed' && taskResult && (
          <div className="space-y-6">
            <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 flex items-center gap-3">
              <span className="material-symbols-outlined text-emerald-600">check_circle</span>
              <div>
                <p className="text-sm font-bold text-emerald-800">解析完成</p>
                <p className="text-xs text-emerald-600">{taskResult.fileName}</p>
              </div>
            </div>

            {/* 解析内容预览 */}
            {taskResult.parsedContent && (
              <div className="bg-white border border-slate-100 rounded-xl p-6 shadow-sm">
                <h3 className="text-sm font-bold text-slate-800 mb-3 flex items-center gap-2">
                  <span className="material-symbols-outlined text-primary text-[18px]">article</span>
                  文档内容摘要
                </h3>
                <p className="text-sm text-slate-600 leading-relaxed whitespace-pre-wrap">{taskResult.parsedContent}</p>
              </div>
            )}

            {/* AI 分析结果 */}
            {taskResult.aiAnalysis && (
              <div className="bg-white border border-slate-100 rounded-xl p-6 shadow-sm">
                <h3 className="text-sm font-bold text-slate-800 mb-3 flex items-center gap-2">
                  <span className="material-symbols-outlined text-accent-red text-[18px]">psychology</span>
                  思政价值分析
                </h3>
                <p className="text-sm text-slate-600 leading-relaxed whitespace-pre-wrap">{taskResult.aiAnalysis}</p>
              </div>
            )}

            <button
              onClick={handleReset}
              className="flex items-center gap-2 px-5 py-2.5 bg-slate-100 text-slate-700 rounded-xl text-sm font-medium hover:bg-slate-200 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              上传新文件
            </button>
          </div>
        )}

        {/* 解析失败 */}
        {status === 'failed' && (
          <div className="space-y-4">
            <div className="bg-red-50 border border-red-200 rounded-xl p-4 flex items-center gap-3">
              <span className="material-symbols-outlined text-red-600">error</span>
              <div>
                <p className="text-sm font-bold text-red-800">解析失败</p>
                <p className="text-xs text-red-600">{error}</p>
              </div>
            </div>
            <button
              onClick={handleReset}
              className="flex items-center gap-2 px-5 py-2.5 bg-slate-100 text-slate-700 rounded-xl text-sm font-medium hover:bg-slate-200 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              重新上传
            </button>
          </div>
        )}
      </div>
    </div>
  );
};