import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  useEffect,
  useState,
  type FormEvent,
} from "react";
import { Link, useOutletContext } from "react-router";

import {
  createDeal,
  type CreateDealRequest,
  type DealSort,
  type DealStatus,
} from "../features/deals/dealApi";
import {
  getDealErrorMessage,
  getDealFieldErrors,
} from "../features/deals/dealErrors";
import {
  dealListQueryOptions,
} from "../features/deals/dealQueries";
import { isInvalidLegalEntitySelection } from "../features/organization/organizationErrors";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";
import { DealMembershipBootstrapState } from "./DealMembershipBootstrapState";

const PAGE_SIZE = 10;
const STATUS_OPTIONS: DealStatus[] = [
  "DRAFT",
  "ACTIVE",
  "CANCELLED",
  "COMPLETED",
  "ARCHIVED",
];
const SORT_OPTIONS: Array<{ value: DealSort; label: string }> = [
  { value: "createdAt,desc", label: "En yeni oluşturulan" },
  { value: "createdAt,asc", label: "En eski oluşturulan" },
  { value: "title,asc", label: "Başlık A–Z" },
  { value: "title,desc", label: "Başlık Z–A" },
];
const STATUS_LABELS: Record<DealStatus, string> = {
  DRAFT: "Taslak",
  ACTIVE: "Aktif",
  CANCELLED: "İptal edildi",
  COMPLETED: "Tamamlandı",
  ARCHIVED: "Arşivlendi",
};
const DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  dateStyle: "medium",
  timeStyle: "short",
});

function statusLabel(status: DealStatus): string {
  return STATUS_LABELS[status];
}

function formatDate(value: string): string {
  return DATE_FORMATTER.format(new Date(value));
}

interface CreateDealFormProps {
  error: unknown;
  isPending: boolean;
  onSubmit: (request: CreateDealRequest) => void;
}

function CreateDealForm({
  error,
  isPending,
  onSubmit,
}: CreateDealFormProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [clientTitleError, setClientTitleError] = useState<string>();
  const serverErrors = getDealFieldErrors(error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      setClientTitleError("Deal başlığını girin.");
      return;
    }
    onSubmit({
      title: normalizedTitle,
      description: description.trim() || undefined,
    });
  }

  return (
    <section className="workspace-panel deal-create-panel" id="create-deal">
      <div className="panel-heading">
        <span className="section-kicker">Yeni Deal</span>
        <h2>Bir taslak oluşturun</h2>
        <p>
          Aktif legal entity ilk katılımcı olur. Yalnızca başlık zorunludur.
        </p>
      </div>

      {error ? (
        <p className="form-alert panel-alert" role="alert">
          {getDealErrorMessage(error)}
        </p>
      ) : null}

      <form className="auth-form deal-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="deal-title">Başlık</label>
          <input
            id="deal-title"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value);
              setClientTitleError(undefined);
            }}
            maxLength={200}
            required
            aria-invalid={Boolean(clientTitleError ?? serverErrors.title)}
          />
          {clientTitleError ?? serverErrors.title ? (
            <span className="field-error">
              {clientTitleError ?? serverErrors.title}
            </span>
          ) : null}
        </div>
        <div className="field-group">
          <label htmlFor="deal-description">Açıklama</label>
          <textarea
            id="deal-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={4000}
            rows={5}
          />
          {serverErrors.description ? (
            <span className="field-error">{serverErrors.description}</span>
          ) : null}
        </div>
        <button className="primary-button" type="submit" disabled={isPending}>
          {isPending ? "Deal oluşturuluyor…" : "Taslak Deal oluştur"}
        </button>
      </form>
    </section>
  );
}

export function DealListPage() {
  const {
    selectedLegalEntityId,
    selectedMembership,
    selectionNotice,
    clearInvalidSelection,
    membershipsPending,
    membershipsError,
    membershipsFetching,
    refetchMemberships,
  } = useOutletContext<AuthenticatedWorkspaceContext>();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<DealStatus | undefined>();
  const [sort, setSort] = useState<DealSort>("createdAt,desc");
  const [page, setPage] = useState(0);
  const [creationNotice, setCreationNotice] = useState<string>();
  const parameters = { status, sort, page, size: PAGE_SIZE };
  const dealsQuery = useQuery(
    dealListQueryOptions(selectedLegalEntityId, parameters),
  );
  const invalidSelection = isInvalidLegalEntitySelection(dealsQuery.error);

  useEffect(() => {
    if (invalidSelection) {
      clearInvalidSelection();
    }
  }, [clearInvalidSelection, invalidSelection]);

  const createMutation = useMutation({
    mutationFn: (request: CreateDealRequest) =>
      createDeal(selectedLegalEntityId!, request),
    onSuccess: async (created) => {
      setStatus(undefined);
      setSort("createdAt,desc");
      setPage(0);
      setCreationNotice(
        `${created.reference} oluşturuldu ve en yeni Deal olarak listelendi.`,
      );
      await queryClient.invalidateQueries({
        queryKey: ["deals", selectedLegalEntityId, "list"],
      });
    },
    onError: (error) => {
      if (isInvalidLegalEntitySelection(error)) {
        clearInvalidSelection();
      }
    },
  });

  function changeStatus(value: string) {
    setStatus(value ? (value as DealStatus) : undefined);
    setPage(0);
    setCreationNotice(undefined);
  }

  function changeSort(value: DealSort) {
    setSort(value);
    setPage(0);
    setCreationNotice(undefined);
  }

  if (membershipsPending) {
    return (
      <DealMembershipBootstrapState
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (membershipsError) {
    return (
      <DealMembershipBootstrapState
        error={membershipsError}
        isFetching={membershipsFetching}
        onRetry={refetchMemberships}
      />
    );
  }

  if (!selectedLegalEntityId || !selectedMembership) {
    return (
      <main className="workspace-main deal-workspace">
        <div className="workspace-column">
          <span className="section-kicker">Deal çalışma alanı</span>
          <h1>Aktif legal entity seçin.</h1>
          <p className="workspace-lead">
            Deal istekleri yalnız seçili ve sunucuda doğrulanan legal entity
            bağlamıyla yapılır. Üst menüden bir seçim yapın.
          </p>
          {selectionNotice ? (
            <p className="form-notice workspace-notice" role="status">
              {selectionNotice}
            </p>
          ) : null}
          <Link className="primary-link-button" to="/app">
            Organizasyonlara dön
          </Link>
        </div>
      </main>
    );
  }

  const deals = dealsQuery.data?.items ?? [];
  const isFiltered = Boolean(status);

  return (
    <main className="workspace-main deal-workspace">
      <div className="workspace-column">
        <span className="section-kicker">Deal çalışma alanı</span>
        <h1>{selectedMembership.legalName} Deals</h1>
        <p className="workspace-lead">
          Gerçek Core API üzerinden Deal oluşturun, filtreleyin ve detaylarını
          yönetin.
        </p>

        {creationNotice ? (
          <p className="success-notice workspace-notice" role="status">
            {creationNotice}
          </p>
        ) : null}

        <div className="deal-layout">
          <section className="workspace-panel deal-list-panel">
            <div className="panel-heading deal-list-heading">
              <div>
                <span className="section-kicker">Katılımcı olduğunuz kayıtlar</span>
                <h2>Deals</h2>
              </div>
              <a className="secondary-link-button" href="#create-deal">
                Yeni Deal
              </a>
            </div>

            <div className="deal-filters" aria-label="Deal liste kontrolleri">
              <label>
                <span>Durum</span>
                <select
                  value={status ?? ""}
                  onChange={(event) => changeStatus(event.target.value)}
                >
                  <option value="">Tüm durumlar</option>
                  {STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {statusLabel(option)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Sıralama</span>
                <select
                  value={sort}
                  onChange={(event) =>
                    changeSort(event.target.value as DealSort)
                  }
                >
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {dealsQuery.isPending ? (
              <div className="panel-loading" role="status">
                <span className="loading-line" aria-hidden="true" />
                <p>Deals yükleniyor…</p>
              </div>
            ) : null}

            {dealsQuery.isError && !invalidSelection ? (
              <div className="deal-state" role="alert">
                <h3>Deal listesi alınamadı</h3>
                <p>{getDealErrorMessage(dealsQuery.error)}</p>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => void dealsQuery.refetch()}
                  disabled={dealsQuery.isFetching}
                >
                  {dealsQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
                </button>
              </div>
            ) : null}

            {dealsQuery.isSuccess && deals.length === 0 ? (
              <div className="deal-state deal-empty-state">
                <h3>
                  {isFiltered
                    ? "Bu filtreyle eşleşen Deal yok."
                    : "Henüz bir Deal yok."}
                </h3>
                <p>
                  {isFiltered
                    ? "Başka bir durum seçin veya tüm Deal’leri görüntüleyin."
                    : "İlk taslağı oluşturarak bu çalışma alanını başlatın."}
                </p>
                {isFiltered ? (
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() => changeStatus("")}
                  >
                    Filtreyi temizle
                  </button>
                ) : (
                  <a className="primary-link-button" href="#create-deal">
                    İlk Deal’i oluştur
                  </a>
                )}
              </div>
            ) : null}

            {deals.length > 0 ? (
              <div className="deal-list">
                {deals.map((deal) => (
                  <Link
                    className="deal-list-item"
                    key={deal.id}
                    to={`/app/deals/${deal.id}`}
                  >
                    <div>
                      <span className="deal-reference">{deal.reference}</span>
                      <h3>{deal.title}</h3>
                      <p>
                        Güncellendi {formatDate(deal.updatedAt)} · Sürüm{" "}
                        {deal.version}
                      </p>
                    </div>
                    <div className="deal-list-meta">
                      <span className="status-badge" data-status={deal.status}>
                        {statusLabel(deal.status)}
                      </span>
                      <span>{deal.lifecycle}</span>
                    </div>
                  </Link>
                ))}
              </div>
            ) : null}

            {dealsQuery.data && dealsQuery.data.totalPages > 0 ? (
              <div className="pagination">
                <button
                  className="text-button"
                  type="button"
                  disabled={page === 0 || dealsQuery.isFetching}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Önceki
                </button>
                <span>
                  Sayfa {dealsQuery.data.page + 1} /{" "}
                  {dealsQuery.data.totalPages} ·{" "}
                  {dealsQuery.data.totalElements} kayıt
                </span>
                <button
                  className="text-button"
                  type="button"
                  disabled={
                    page + 1 >= dealsQuery.data.totalPages ||
                    dealsQuery.isFetching
                  }
                  onClick={() => setPage((current) => current + 1)}
                >
                  Sonraki
                </button>
              </div>
            ) : null}
          </section>

          <CreateDealForm
            key={creationNotice ?? "new-deal"}
            error={createMutation.error}
            isPending={createMutation.isPending}
            onSubmit={(request) => {
              setCreationNotice(undefined);
              createMutation.mutate(request);
            }}
          />
        </div>
      </div>
    </main>
  );
}
