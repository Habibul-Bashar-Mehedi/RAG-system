import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface DocumentItem { id: string; filename: string; status: string; pageCount?: number; chunkCount?: number; errorMessage?: string; }
interface Session { id: string; title: string; createdAt?: string; }
interface Message { role: string; content: string; sources?: { filename: string; pageNumber: number; snippet: string }[]; }

@Component({
  selector: 'rag-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './styles.css'
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly userId = this.getUserId();
  activeView: 'library' | 'chat' = 'library';
  documents: DocumentItem[] = [];
  sessions: Session[] = [];
  currentSession: Session | null = null;
  messages: Message[] = [];
  selectedDocument = '';
  newTitle = '';
  question = '';
  uploadName = '';
  busy = false;
  loading = true;
  error = '';
  notice = '';

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    this.loading = true;
    this.http.get<DocumentItem[]>('/api/documents', { headers: { 'X-User-Id': this.userId } }).subscribe({
      next: docs => { this.documents = docs; this.loading = false; },
      error: err => this.fail(err)
    });
    this.http.get<Session[]>('/api/chat/sessions', { headers: { 'X-User-Id': this.userId } }).subscribe({ next: s => this.sessions = s });
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.busy = true; this.uploadName = file.name; this.notice = 'Uploading and indexing your document…';
    const body = new FormData(); body.append('file', file);
    this.http.post<DocumentItem>('/api/documents', body, { headers: { 'X-User-Id': this.userId } }).subscribe({
      next: () => { this.busy = false; this.notice = 'Document queued. We’ll let you know when it’s ready.'; input.value = ''; this.refresh(); },
      error: err => this.fail(err)
    });
  }

  removeDocument(doc: DocumentItem): void {
    if (!confirm(`Remove ${doc.filename}?`)) return;
    this.http.delete(`/api/documents/${doc.id}`, { headers: { 'X-User-Id': this.userId } }).subscribe({ next: () => { this.notice = 'Document removed.'; this.refresh(); }, error: err => this.fail(err) });
  }

  startChat(doc?: DocumentItem): void {
    this.selectedDocument = doc?.id ?? '';
    this.activeView = 'chat';
    if (doc) this.createSession();
  }

  createSession(): void {
    const body: { documentId?: string; title?: string } = {};
    if (this.selectedDocument) body.documentId = this.selectedDocument;
    if (this.newTitle.trim()) body.title = this.newTitle.trim();
    this.http.post<Session>('/api/chat/sessions', body, { headers: { 'X-User-Id': this.userId } }).subscribe({
      next: session => { this.sessions = [session, ...this.sessions]; this.newTitle = ''; this.openSession(session); }, error: err => this.fail(err)
    });
  }

  openSession(session: Session): void {
    this.currentSession = session; this.activeView = 'chat'; this.messages = [];
    this.http.get<{ session: Session; messages: Message[] }>(`/api/chat/sessions/${session.id}`, { headers: { 'X-User-Id': this.userId } }).subscribe({ next: detail => this.messages = detail.messages, error: err => this.fail(err) });
  }

  send(): void {
    const text = this.question.trim();
    if (!text || !this.currentSession || this.busy) return;
    this.messages = [...this.messages, { role: 'USER', content: text }]; this.question = ''; this.busy = true;
    this.http.post<{ answer: string; sources: Message['sources'] }>(`/api/chat/sessions/${this.currentSession.id}/messages`, { question: text }, { headers: { 'X-User-Id': this.userId } }).subscribe({
      next: answer => { this.messages = [...this.messages, { role: 'ASSISTANT', content: answer.answer, sources: answer.sources }]; this.busy = false; }, error: err => this.fail(err)
    });
  }

  handleEnter(event: Event): void {
    if (!(event as KeyboardEvent).shiftKey) { event.preventDefault(); this.send(); }
  }

  deleteSession(session: Session, event: Event): void {
    event.stopPropagation();
    this.http.delete(`/api/chat/sessions/${session.id}`, { headers: { 'X-User-Id': this.userId } }).subscribe({ next: () => { this.sessions = this.sessions.filter(item => item.id !== session.id); if (this.currentSession?.id === session.id) { this.currentSession = null; this.messages = []; } }, error: err => this.fail(err) });
  }

  statusLabel(status: string): string { return status === 'READY' ? 'Ready' : status === 'PROCESSING' ? 'Indexing' : status === 'PENDING' ? 'Queued' : 'Needs attention'; }
  readyCount(): number { return this.documents.filter(doc => doc.status === 'READY').length; }
  private fail(err: { error?: { message?: string }; message?: string }): void { this.busy = false; this.loading = false; this.error = err.error?.message ?? err.message ?? 'Something went wrong.'; }
  private getUserId(): string { const key = 'rag_user_id'; let id = localStorage.getItem(key); if (!id) { id = crypto.randomUUID(); localStorage.setItem(key, id); } return id; }
}
