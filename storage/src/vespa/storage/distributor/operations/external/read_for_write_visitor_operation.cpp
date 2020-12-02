// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "read_for_write_visitor_operation.h"
#include "visitoroperation.h"
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/operationowner.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".operations.external.read_for_write_visitor_operation");

namespace storage::distributor {

ReadForWriteVisitorOperationStarter::ReadForWriteVisitorOperationStarter(
        std::shared_ptr<VisitorOperation> visitor_op,
        OperationSequencer& operation_sequencer,
        OperationOwner& stable_operation_owner,
        PendingMessageTracker& message_tracker)
    : _visitor_op(std::move(visitor_op)),
      _operation_sequencer(operation_sequencer),
      _stable_operation_owner(stable_operation_owner),
      _message_tracker(message_tracker)
{
}

ReadForWriteVisitorOperationStarter::~ReadForWriteVisitorOperationStarter() = default;

void ReadForWriteVisitorOperationStarter::onClose(DistributorMessageSender& sender) {
    _visitor_op->onClose(sender);
}

void ReadForWriteVisitorOperationStarter::onStart(DistributorMessageSender& sender) {
    if (_visitor_op->verify_command_and_expand_buckets(sender)) {
        assert(!_visitor_op->has_sent_reply());
        auto maybe_bucket = _visitor_op->first_bucket_to_visit();
        if (!maybe_bucket) {
            LOG(debug, "No buckets found to visit, tagging visitor complete");
            // No buckets to be found, start op to trigger immediate reply.
            _visitor_op->start(sender, _startTime);
            assert(_visitor_op->has_sent_reply());
            return;
        }
        auto bucket_handle = _operation_sequencer.try_acquire(*maybe_bucket);
        if (!bucket_handle.valid()) {
            LOG(debug, "An operation is already pending for bucket %s, failing visitor",
                maybe_bucket->toString().c_str());
            _visitor_op->fail_with_bucket_already_locked(sender);
            return;
        }
        LOG(debug, "Possibly deferring start of visitor for bucket %s", maybe_bucket->toString().c_str());
        _message_tracker.run_once_no_pending_for_bucket(
                *maybe_bucket,
                make_deferred_task([self = shared_from_this(), handle = std::move(bucket_handle)](TaskRunState state) mutable {
                    LOG(debug, "Starting deferred visitor");
                    self->_visitor_op->assign_bucket_lock_handle(std::move(handle));
                    if (state == TaskRunState::OK) {
                        // Once started, ownership of _visitor_op will pass to the Distributor's OperationOwner
                        self->_stable_operation_owner.start(self->_visitor_op, 120/*TODO*/);
                    } else {
                        self->_visitor_op->onClose(self->_stable_operation_owner.sender());
                    }
                }));
    } else {
        LOG(debug, "Failed verification of visitor, responding immediately");
        assert(_visitor_op->has_sent_reply());
    }
}

void ReadForWriteVisitorOperationStarter::onReceive(DistributorMessageSender& sender,
                                                    const std::shared_ptr<api::StorageReply> & msg) {
    _visitor_op->onReceive(sender, msg);
}

}
