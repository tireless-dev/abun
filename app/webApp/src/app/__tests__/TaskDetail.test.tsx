import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TaskDetailSheet, TaskStack, type TaskWithStatus } from '../App.tsx';

const task: TaskWithStatus = {
  id: 'task-1',
  title: 'Write alignment tests',
  detail: 'Keep task actions in detail.',
  is_deleted: false,
  server_version: 1,
  status: 'PENDING',
};

describe('aligned task surfaces', () => {
  it('uses a clickable compact row without inline execution actions', () => {
    const onOpen = vi.fn();
    render(<TaskStack tasks={[task]} empty="Empty" onOpen={onOpen} />);

    fireEvent.click(screen.getByRole('button', { name: /Write alignment tests/i }));

    expect(onOpen).toHaveBeenCalledWith('task-1');
    expect(screen.queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('opens read-only with a handle, history, editing, and confirmed deletion', () => {
    const onDelete = vi.fn();
    const { container } = render(
      <TaskDetailSheet
        task={task}
        tasks={[task]}
        events={[{ id: 'event-1', task_id: task.id, journal_date: '2026-07-13', event_type: 'CREATED', event_time: '2026-07-13T08:00:00.000Z', is_deleted: false, server_version: 1 }]}
        working={false}
        onDismiss={vi.fn()}
        onSave={vi.fn()}
        onEvent={vi.fn()}
        onFocus={vi.fn()}
        onDelete={onDelete}
      />,
    );

    expect(container.querySelector('.sheet-handle')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'History' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Title')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Edit task' }));
    expect(screen.getByLabelText('Title')).toHaveValue(task.title);
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(screen.getByRole('alertdialog', { name: 'Delete task confirmation' })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[1]);
    expect(onDelete).toHaveBeenCalledOnce();
  });
});
