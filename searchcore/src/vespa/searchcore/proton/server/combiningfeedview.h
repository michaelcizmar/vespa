// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include "replaypacketdispatcher.h"
#include "ibucketstatecalculator.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class DocumentOperation;

class CombiningFeedView : public IFeedView
{
private:
    const std::shared_ptr<const document::DocumentTypeRepo>          _repo;
    std::vector<IFeedView::SP>                    _views;
    std::vector<const ISimpleDocumentMetaStore *> _metaStores;
    std::shared_ptr<IBucketStateCalculator>       _calc;
    bool                                          _clusterUp;
    bool                                          _forceReady;
    document::BucketSpace                         _bucketSpace;

    const ISimpleDocumentMetaStore * getDocumentMetaStorePtr() const override;

    void findPrevDbdId(const document::GlobalId &gid, DocumentOperation &op);
    uint32_t getReadyFeedViewId() const { return 0u; }
    uint32_t getRemFeedViewId() const { return 1u; }
    uint32_t getNotReadyFeedViewId() const { return 2u; }

    IFeedView * getReadyFeedView() {
        return _views[getReadyFeedViewId()].get();
    }

    IFeedView * getRemFeedView() {
        return _views[getRemFeedViewId()].get();
    }

    IFeedView * getNotReadyFeedView() {
        return _views[getNotReadyFeedViewId()].get();
    }

    bool hasNotReadyFeedView() const {
        return _views.size() > getNotReadyFeedViewId();
    }

    vespalib::Trinary shouldBeReady(const document::BucketId &bucket) const;
    void forceCommit(const CommitParam & param, const DoneCallback& onDone) override;
public:
    using SP = std::shared_ptr<CombiningFeedView>;

    CombiningFeedView(const std::vector<IFeedView::SP> &views,
                      document::BucketSpace bucketSpace,
                      const std::shared_ptr<IBucketStateCalculator> &calc);

    ~CombiningFeedView() override;

    const std::shared_ptr<const document::DocumentTypeRepo> & getDocumentTypeRepo() const override;

    /**
     * Similar to IPersistenceHandler functions.
     */

    void preparePut(PutOperation &putOp) override;
    void handlePut(FeedToken token, const PutOperation &putOp) override;
    void prepareUpdate(UpdateOperation &updOp) override;
    void handleUpdate(FeedToken token, const UpdateOperation &updOp) override;
    void prepareRemove(RemoveOperation &rmOp) override;
    void handleRemove(FeedToken token, const RemoveOperation &rmOp) override;
    void prepareDeleteBucket(DeleteBucketOperation &delOp) override;
    bool isMoveStillValid(const MoveOperation & moveOp) const override;
    void prepareMove(MoveOperation &putOp) override;
    void handleDeleteBucket(const DeleteBucketOperation &delOp, const DoneCallback& onDone) override;
    void handleMove(const MoveOperation &moveOp, const DoneCallback& onDone) override;
    void heartBeat(search::SerialNum serialNum, const DoneCallback& onDone) override;
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp, const DoneCallback& onDone) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, const DoneCallback& onDone) override;

    // Called by document db executor
    void setCalculator(const std::shared_ptr<IBucketStateCalculator> &newCalc);
};

} // namespace proton

