import { useEffect, useRef, useState } from 'react';
import { gateway } from '../../api/client.js';
import { emptyEventDraft, toFixedLayoutEventRequest, validateEventDraft } from './event-draft.js';

/** @typedef {import('../../api/types.js').CreateEventRequest} CreateEventRequest */
/** @typedef {{ uploadPoster(file: File, options?: { signal?: AbortSignal }): Promise<import('../../api/types.js').ApiResult<{ url: string }>> }} EventEditorGateway */

const clientField = (field) => field.startsWith('layout.') ? field.slice('layout.'.length) : field;
const controlProps = (errors, field, id) => ({
  id,
  'aria-invalid': Boolean(errors[field]),
  'aria-describedby': errors[field] ? `${id}-error` : undefined,
});
const fieldError = (errors, field, id) => errors[field]
  ? <small id={`${id}-error`} className="field-error" role="alert">{errors[field]}</small>
  : null;

/**
 * Emits only when the draft is valid and this editor observed a successful poster upload.
 * @param {ReturnType<typeof emptyEventDraft>} draft
 * @param {boolean} posterUploaded
 * @param {(request: CreateEventRequest) => void} emit
 */
export function prepareCreateEvent(draft, posterUploaded, emit) {
  const errors = validateEventDraft(draft);
  if (!posterUploaded || !draft.artworkUrl) errors.artworkUrl = 'Upload the event poster before submitting.';
  if (!Object.keys(errors).length) emit(toFixedLayoutEventRequest(draft));
  return errors;
}

/**
 * @param {{ client?: EventEditorGateway, onCreate: (request: CreateEventRequest) => Promise<import('../../api/types.js').ApiResult<unknown>>, initialDraft?: ReturnType<typeof emptyEventDraft>, initialFieldErrors?: Record<string, string> }} props
 */
export function EventEditor({ client = gateway?.organizer, onCreate, initialDraft, initialFieldErrors = {} }) {
  const [draft, setDraft] = useState(() => initialDraft ?? emptyEventDraft());
  const [fieldErrors, setFieldErrors] = useState(initialFieldErrors);
  const [posterPreview, setPosterPreview] = useState('');
  const [posterUploaded, setPosterUploaded] = useState(false);
  const [uploadingPoster, setUploadingPoster] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [status, setStatus] = useState('');
  const uploadSequence = useRef(0);
  const posterInput = useRef(null);

  useEffect(() => () => {
    if (posterPreview.startsWith('blob:')) URL.revokeObjectURL(posterPreview);
  }, [posterPreview]);
  useEffect(() => () => { uploadSequence.current += 1; }, []);

  const change = (field, value) => {
    setDraft((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const selectPoster = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const uploadId = ++uploadSequence.current;
    setPosterPreview(URL.createObjectURL(file));
    setPosterUploaded(false);
    setUploadingPoster(true);
    setStatus('Uploading poster…');
    setFieldErrors((current) => ({ ...current, artworkUrl: '' }));
    const result = await client.uploadPoster(file);
    if (uploadId !== uploadSequence.current) return;
    setUploadingPoster(false);
    if (!result.ok) {
      change('artworkUrl', '');
      setStatus(result.error?.message ?? 'Poster upload failed.');
      setFieldErrors((current) => ({
        ...current,
        artworkUrl: result.error?.fields?.file ?? result.error?.message ?? 'Poster upload failed.',
      }));
      return;
    }
    change('artworkUrl', result.data.url);
    setPosterUploaded(true);
    setStatus('Poster uploaded.');
  };

  const submit = async (event) => {
    event.preventDefault();
    let request;
    const errors = prepareCreateEvent(draft, posterUploaded, (value) => { request = value; });
    if (Object.keys(errors).length) {
      setFieldErrors(errors);
      setStatus('Check the highlighted event fields.');
      return;
    }
    setSubmitting(true);
    setStatus('Submitting event…');
    const result = await onCreate(request);
    setSubmitting(false);
    if (!result.ok) {
      setFieldErrors(Object.fromEntries(Object.entries(result.error?.fields ?? {})
        .map(([field, message]) => [clientField(field), message])));
      setStatus(result.error?.message ?? 'The event could not be submitted.');
      return;
    }
    setDraft(emptyEventDraft());
    setFieldErrors({});
    setPosterPreview('');
    setPosterUploaded(false);
    if (posterInput.current) posterInput.current.value = '';
    setStatus('Submitted for approval.');
  };

  return <section className="organizer-event-editor" aria-labelledby="organizer-event-editor-heading">
    <h3 id="organizer-event-editor-heading">Submit event</h3>
    {status && <p className="profile-panel__message" role={Object.keys(fieldErrors).length ? 'alert' : 'status'}>{status}</p>}
    <form className="organizer-event-form" onSubmit={submit} noValidate>
      <label htmlFor="event-name">Name</label>
      <input {...controlProps(fieldErrors, 'name', 'event-name')} required value={draft.name} onChange={(event) => change('name', event.target.value)} />
      {fieldError(fieldErrors, 'name', 'event-name')}

      <label htmlFor="event-venue">Venue</label>
      <input {...controlProps(fieldErrors, 'venue', 'event-venue')} required value={draft.venue} onChange={(event) => change('venue', event.target.value)} />
      {fieldError(fieldErrors, 'venue', 'event-venue')}

      <label className="organizer-poster-field" htmlFor="event-poster">Event poster</label>
      <input {...controlProps(fieldErrors, 'artworkUrl', 'event-poster')} ref={posterInput} required type="file" accept="image/jpeg,image/png,image/webp" onChange={selectPoster} />
      <span className="organizer-poster-status">{uploadingPoster ? 'Uploading poster…' : posterUploaded ? 'Poster uploaded.' : 'JPEG, PNG or WebP.'}</span>
      {fieldError(fieldErrors, 'artworkUrl', 'event-poster')}
      {posterPreview && <figure className="organizer-poster-preview"><img src={posterPreview} alt="Event poster preview" /></figure>}

      <label htmlFor="event-starts">Event starts</label>
      <input {...controlProps(fieldErrors, 'startsAt', 'event-starts')} required type="datetime-local" value={draft.startsAt} onChange={(event) => change('startsAt', event.target.value)} />
      {fieldError(fieldErrors, 'startsAt', 'event-starts')}
      <label htmlFor="event-ends">Event ends</label>
      <input {...controlProps(fieldErrors, 'endsAt', 'event-ends')} type="datetime-local" value={draft.endsAt} onChange={(event) => change('endsAt', event.target.value)} />
      {fieldError(fieldErrors, 'endsAt', 'event-ends')}

      <fieldset className="organizer-sale-type">
        <legend>Sale type</legend>
        <label><input type="radio" name="saleType" value="STANDARD" checked={draft.saleType === 'STANDARD'} onChange={(event) => change('saleType', event.target.value)} /> Standard</label>
        <label><input type="radio" name="saleType" value="FLASH" checked={draft.saleType === 'FLASH'} onChange={(event) => change('saleType', event.target.value)} /> Flash</label>
        {fieldError(fieldErrors, 'saleType', 'event-sale-type')}
      </fieldset>
      {draft.saleType === 'FLASH' && <>
        <label htmlFor="event-queue-opens">Queue opens</label>
        <input {...controlProps(fieldErrors, 'queueOpensAt', 'event-queue-opens')} required type="datetime-local" value={draft.queueOpensAt} onChange={(event) => change('queueOpensAt', event.target.value)} />
        {fieldError(fieldErrors, 'queueOpensAt', 'event-queue-opens')}
      </>}
      <label htmlFor="event-sale-starts">Sale starts</label>
      <input {...controlProps(fieldErrors, 'saleStartsAt', 'event-sale-starts')} required type="datetime-local" value={draft.saleStartsAt} onChange={(event) => change('saleStartsAt', event.target.value)} />
      {fieldError(fieldErrors, 'saleStartsAt', 'event-sale-starts')}
      <label htmlFor="event-sale-ends">Sale ends</label>
      <input {...controlProps(fieldErrors, 'saleEndsAt', 'event-sale-ends')} required type="datetime-local" value={draft.saleEndsAt} onChange={(event) => change('saleEndsAt', event.target.value)} />
      {fieldError(fieldErrors, 'saleEndsAt', 'event-sale-ends')}

      <fieldset className="organizer-zone">
        <legend>General Admission · Standing</legend>
        <label htmlFor="event-ga-capacity">Capacity</label><input {...controlProps(fieldErrors, 'generalAdmissionCapacity', 'event-ga-capacity')} required type="number" min="1" value={draft.generalAdmissionCapacity} onChange={(event) => change('generalAdmissionCapacity', event.target.value)} />{fieldError(fieldErrors, 'generalAdmissionCapacity', 'event-ga-capacity')}
        <label htmlFor="event-ga-price">Price</label><input {...controlProps(fieldErrors, 'generalAdmissionPrice', 'event-ga-price')} required type="number" min="0.01" step="0.01" value={draft.generalAdmissionPrice} onChange={(event) => change('generalAdmissionPrice', event.target.value)} />{fieldError(fieldErrors, 'generalAdmissionPrice', 'event-ga-price')}
      </fieldset>
      <fieldset className="organizer-zone">
        <legend>Left Premium · Seated</legend>
        <label htmlFor="event-left-capacity">Capacity</label><input {...controlProps(fieldErrors, 'leftPremiumCapacity', 'event-left-capacity')} required type="number" min="1" value={draft.leftPremiumCapacity} onChange={(event) => change('leftPremiumCapacity', event.target.value)} />{fieldError(fieldErrors, 'leftPremiumCapacity', 'event-left-capacity')}
        <label htmlFor="event-left-price">Price</label><input {...controlProps(fieldErrors, 'leftPremiumPrice', 'event-left-price')} required type="number" min="0.01" step="0.01" value={draft.leftPremiumPrice} onChange={(event) => change('leftPremiumPrice', event.target.value)} />{fieldError(fieldErrors, 'leftPremiumPrice', 'event-left-price')}
      </fieldset>
      <fieldset className="organizer-zone">
        <legend>Right Premium · Seated</legend>
        <label htmlFor="event-right-capacity">Capacity</label><input {...controlProps(fieldErrors, 'rightPremiumCapacity', 'event-right-capacity')} required type="number" min="1" value={draft.rightPremiumCapacity} onChange={(event) => change('rightPremiumCapacity', event.target.value)} />{fieldError(fieldErrors, 'rightPremiumCapacity', 'event-right-capacity')}
        <label htmlFor="event-right-price">Price</label><input {...controlProps(fieldErrors, 'rightPremiumPrice', 'event-right-price')} required type="number" min="0.01" step="0.01" value={draft.rightPremiumPrice} onChange={(event) => change('rightPremiumPrice', event.target.value)} />{fieldError(fieldErrors, 'rightPremiumPrice', 'event-right-price')}
      </fieldset>
      <button type="submit" disabled={uploadingPoster || submitting}>{submitting ? 'Submitting…' : 'Submit for approval'}</button>
    </form>
  </section>;
}
