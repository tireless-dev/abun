import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from '../App.tsx';

describe('App', () => {
  it('shows auth gate by default', () => {
    localStorage.removeItem('abun_access_token');
    localStorage.removeItem('abun_user_id');
    render(<App />);
    expect(screen.getByText('Login with email OTP to access your cloud workspace.')).toBeInTheDocument();
  });
});
